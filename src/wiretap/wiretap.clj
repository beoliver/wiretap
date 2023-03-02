(ns wiretap.wiretap)

(def ^:private ^:dynamic *context* nil)

(defn- ^::exclude wiretap-var! [f var-obj]
  (let [var-meta (meta var-obj)
        value (or (::wiretapped var-meta) (var-get var-obj))]
    (when (and (fn? value) (not (::exclude var-meta)))
      (letfn [(wiretapped [& args]
                (let [context {:ns (:ns var-meta)
                               :name (:name var-meta)
                               :function value
                               :args args
                               :depth ((fnil inc -1) (:depth wiretap.wiretap/*context*))
                               :id (str (random-uuid))
                               :thread (.getName (Thread/currentThread))
                               :stack (.getStackTrace (Thread/currentThread))
                               :start (. System (nanoTime))
                               :parent (:id wiretap.wiretap/*context*)}]
                  (try (f (assoc context :pre? true)) (catch Exception _))
                  (let [{:keys [error result] :as response}
                        (try {:result (binding [wiretap.wiretap/*context* context]
                                        (apply value args))}
                             (catch Exception e {:error e}))]
                    (try (f (assoc (merge context response)
                                   :post? true
                                   :stop (. System (nanoTime))))
                         (catch Exception _))
                    (if error (throw error) result))))]
        (doto var-obj
          (alter-var-root (constantly (with-meta wiretapped {::exclude true})))
          (alter-meta! #(assoc % ::wiretapped value))))
      var-obj)))

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

(defn ^::exclude uninstall!
  "Sets the root binding of every applicable var to a be the value before calling
   `install!`. If called without any arguments then all vars available via
   `clojure.core/loaded-libs` will be checked.

   A var is considered applicable if a valid value is present under the metadata
   key `:wiretap.wiretap/wiretapped` and its metadata does not contain the key
   `:wiretap.wiretap/exclude`.

   Returns a coll of all modified vars."
  ([] (uninstall! (mapcat (comp vals ns-interns) (loaded-libs))))
  ([vars] (doall (keep (fn [var-obj]
                         (let [{:keys [::wiretapped ::exclude]} (meta var-obj)]
                           (when (and wiretapped (not exclude))
                             (doto var-obj
                               (alter-var-root (constantly wiretapped))
                               (alter-meta! #(dissoc % ::wiretapped)))))) vars))))
