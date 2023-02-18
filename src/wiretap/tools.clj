(ns wiretap.tools)

(defn ns-vars [& namespaces]
  (mapcat #(vals (ns-interns %)) (set namespaces)))

(defn ns-matches
  "Given an instance of java.util.regex.Pattern, returns a sorted coll of symbols 
   naming the currently loaded libs that are matched by `regex`."
  [regex]
  (->> (loaded-libs)
       (filter 
        (comp (partial re-matches regex) name))))

(defn ns-matches-vars
  "Given an instance of java.util.regex.Pattern, returns a seq of all vars 
   that have been interned in namespaces matched by the regex"
  [regex]
  (apply ns-vars (ns-matches regex)))

(defn wiretapped? [var-obj] 
  (boolean (:wiretap.wiretap/wiretapped (meta var-obj))))

(defn filter-trace [pattern trace]
  (->> (map #(.getClassName %) trace)
       (keep #(re-matches pattern %))))
