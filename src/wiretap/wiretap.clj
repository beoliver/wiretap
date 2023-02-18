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
                  (try (f (assoc context :called :pre)) (catch Exception _))
                  (let [{:keys [error result] :as response}
                        (try {:result (binding [wiretap.wiretap/*context* context]
                                        (apply value args))}
                             (catch Exception e {:error e}))]
                    (try (f (assoc (merge context response)
                                   :called :post
                                   :stop (. System (nanoTime))))
                         (catch Exception _))
                    (if error (throw error) result))))]
        (doto var-obj
          (alter-var-root (constantly (with-meta wiretapped {::exclude true})))
          (alter-meta! #(assoc % ::wiretapped value))))
      var-obj)))

(defn ^::exclude install!
  "For every applicable var in vars - Uninstalls any exitsing wiretap and alters
   the root binding of the var to be a variadic function forming a lexical closure
   over the value of the var and the user provided funtion `f`.

   A var is considered applicable if its metadata does not contain the key
   `:wiretap.wiretap.exclude` and its value implements Fn, i.e. is an object
   created via fn.

   When the resulting _wiretapped_ function is called, a map representing the context
   of the call is passed to `f` both **pre** and **post** computation of the result
   by applying the original value to to any args provided.

   Returns a coll of all modified vars."
  [f vars]
  (doall (keep (partial wiretap-var! f) vars)))

(defn ^::exclude uninstall!
  "Sets the root binding of every applicable var to a be the value before calling
   `install!`. If called without any argments then all vars avaiable via
   `clojure.core/loaded-libs` will be checked.

   A var is considered applicable if a valid value is present under the metadata
   key `:wiretap.wiretap/wiretapped` and its metadata does not contain the key
   `:wiretap.wiretap.exclude`.

   Returns a coll of all modified vars."
  ([] (uninstall! (mapcat (comp vals ns-interns) (loaded-libs))))
  ([vars] (doall (keep (fn [var-obj]
                         (let [{:keys [::wiretapped ::exclude]} (meta var-obj)]
                           (when (and wiretapped (not exclude))
                             (doto var-obj
                               (alter-var-root (constantly wiretapped))
                               (alter-meta! #(dissoc % ::wiretapped)))))) vars))))
