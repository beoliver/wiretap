(ns wiretap.wiretap)

(def ^:private ^:dynamic *context* nil)

(defn- ^::exclude uninstall-wiretap! [var-obj]
  (let [{:keys [::uninstall ::exclude]} (meta var-obj)]
    (when (and uninstall (not exclude))
      (doto var-obj
        (alter-var-root uninstall)
        (alter-meta! #(dissoc % ::uninstall))))))


(defn ^::exclude uninstall!
  "Uninstalls all wiretaps from all applicable vars in vars.

   A var is considered applicable if the key `:wiretap.wiretap/uninstall` 
   is present in the metadata and the key `:wiretap.wiretap/exclude` is not.

   Returns a coll of all modified vars."
  ([] (uninstall! (mapcat (comp vals ns-interns) (all-ns))))
  ([vars] (doall (keep uninstall-wiretap! vars))))


(defn- ^::exclude wrap-wiretapped
  [var-meta additional-context existing-f supplied-f]
  (fn wiretapped [& args]
    (let [context (merge
                   additional-context
                   {:ns (:ns var-meta)
                    :name (:name var-meta)
                    :function existing-f
                    :args args
                    :depth ((fnil inc -1) (:depth wiretap.wiretap/*context*))
                    :id (str (random-uuid))
                    :thread (.getName (Thread/currentThread))
                    :stack (.getStackTrace (Thread/currentThread))
                    :start (. System (nanoTime))
                    :parent (:id wiretap.wiretap/*context*)})]
      (try (supplied-f (assoc context :pre? true)) (catch Exception _))
      (let [{:keys [error result] :as response}
            (try {:result (binding [wiretap.wiretap/*context* context]
                            (apply existing-f args))}
                 (catch Exception e {:error e}))]
        (try (supplied-f (assoc (merge context response)
                                :post? true
                                :stop (. System (nanoTime))))
             (catch Exception _))
        (if error (throw error) result)))))

(defn- ^::exclude wiretap-var! [f var-obj]
  (let [var-meta (meta var-obj)]
    (when-not (::exclude var-meta)
      (uninstall-wiretap! var-obj)
      (let [var-value (var-get var-obj)]
        (when-some [uninstall-fn
                    (cond
                      (instance? clojure.lang.Fn var-value)
                      (let [new-value (wrap-wiretapped var-meta {} var-value f)]
                        (alter-var-root var-obj (constantly new-value))
                              ;; uninstall function is the constant original value
                        (constantly var-value))

                      (instance? clojure.lang.MultiFn var-value)
                      (let [method-table (methods var-value)]
                        (doseq [[dispatch-val user-method] method-table]
                          (let [context {:multimethod? true :dispatch-val dispatch-val}]
                            (remove-method var-value dispatch-val)
                            (.addMethod var-value dispatch-val
                                        (wrap-wiretapped var-meta context user-method f))))
                              ;; to uninstall we remove all of the methods that we added 
                              ;; and replace with the original methods. This means that if a user
                              ;; added a method after we wiretapped it will not be removed - which is
                              ;; probably what we want.
                        (fn [var-value]
                          (doseq [[dispatch-val user-method] method-table]
                            (remove-method var-value dispatch-val)
                            (.addMethod var-value dispatch-val user-method))
                          var-value))
                      :else nil)]
          (alter-meta! var-obj #(assoc % ::uninstall uninstall-fn))
          var-obj)))))


(defn ^::exclude install!
  "For every applicable var in vars - removes any existing wiretap and alters
   the root binding to be a variadic function closing over the value `g` of the
   var and the user provided function `f`.

   > A var is considered applicable if its metadata does not contain the
   > key `:wiretap.wiretap/exclude` and its value implements Fn, i.e. is an
   > object created via `fn`.

   When the resulting \"wiretapped\" function is called, a map representing the
   **context** of the call is first passed to `f` before the result is computed
   by applying `g` to to any args provided. `f` is then called with an updated
   context before the result is returned. In both cases, `f` is executed within
   a `try/catch` on the same thread. The result of calling `f` is discarded.

   Returns a coll of all modified vars.

   The following contextual data is will **always** be present in the map passed
   to `f`:

   | Key         | Value                                                            |
   | ----------- | ---------------------------------------------------------------- |
   | `:id`       | Uniquely identifies the call. Same value for pre and post calls. |
   | `:name`     | A symbol. Taken from the _meta_ of the var.                      |
   | `:ns`       | A namespace. Taken from the _meta_ of the var.                   |
   | `:function` | The value that will be applied to the value of `:args`.          |
   | `:thread`   | The name of the thread.                                          |
   | `:stack`    | The current stacktrace.                                          |
   | `:depth`    | Number of _wiretapped_ function calls on the stack.              |
   | `:args`     | The seq of args that value of `:function` will be applied to.    |
   | `:start`    | Nanoseconds since some fixed but arbitrary origin time.          |
   | `:parent`   | The context of the previous wiretapped function on the stack.    |

   ### Multimethods 

   If the wiretapped var is a multimethod then the following information will also be present.   

   | Key              | Value                                         | 
   | ---------------- | --------------------------------------------- |
   | `:multimethod?`  | `true`                                        |
   | `:dispatch-val`  | The dispatch value used to select the method. |

   ### Pre invocation

   When `f` is called **pre** invocation the following information will also be present.
   | Key     | Value  |
   | ------- | -------|
   | `:pre?` | `true` |

   ### Post invocation

   When `f` is called **post** invocation the following information will also be present.

   | Key       | Value                                                                             |
   | --------- | --------------------------------------------------------------------------------- |
   | `:post?`  | `true`                                                                            |
   | `:stop`   | Nanoseconds since some fixed but arbitrary origin time.                           |
   | `:result` | The result computed by applying the value of `:function` to the value of `:args`. |
   | `:error`  | Any exception caught during computation of the result.                            |
   "
  [f vars]
  (doall (keep (partial wiretap-var! f) vars)))

;; (defn- ^::exclude wiretap-multifn-dispatch! [f var-obj]
;;   (let [var-meta (meta var-obj)
;;         value (var-get var-obj)]
;;     (assert (instance? clojure.lang.MultiFn value) "Only MultiFn's can be wiretapped here")
;;     (let [dispatch-fn (.-dispatchFn value)
;;           field (.getDeclaredField clojure.lang.MultiFn "dispatchFn")]
;;       (.setAccessible field true)
;;       (.set field value (fn [& args]
;;                           (let [dispatch-val (apply dispatch-fn args)]
;;                             (f {:dispatch-val dispatch-val
;;                                 :args args
;;                                 :meta (meta var-obj)})
;;                             dispatch-val)))
;;       (.setAccessible field false))))
