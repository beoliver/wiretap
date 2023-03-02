(ns user
  (:require
   [wiretap.wiretap :as wiretap]
   [wiretap.tools :as tools]
   [clojure.tools.trace :as trace]))

(defn simple [x] (inc x))

(defn call-f [f x] (f x))

(defn pass-simple [x] (call-f simple x))

(comment
  (def events (atom []))
  (wiretap/install! #(swap! events conj %) (tools/ns-vars *ns*))
  (pass-simple 1)
  (wiretap/uninstall!)
  (pass-simple 2)
  (count @events))


(defn ^:wiretap.wiretap/exclude my-trace
  [trace-id-atom {:keys [id pre? depth name ns args result] :as ctx}]
  (let [trace-id (if pre? (gensym "t") (get @trace-id-atom id))
        trace-indent (apply str (take depth (repeat "| ")))
        trace-value (if pre?
                      (str trace-indent (pr-str (cons (symbol (ns-resolve ns name)) args)))
                      (str trace-indent "=> " (pr-str result)))]
    (if pre?
      (swap! trace-id-atom assoc id trace-id)
      (swap! trace-id-atom dissoc id))
    (println (str "TRACE" (str " " trace-id) ": " trace-value))))

(comment
  (def contexts (atom []))
  (wiretap/install! #(swap! contexts conj %) (tools/ns-vars *ns*)) 
  (pass-simple 1)
  (wiretap/uninstall!)
  (run! (partial my-trace (atom {})) @contexts)
  )

(comment



  (defn run-simple [xs] (run! simple xs))


  (defn map-simple [xs] (map simple xs))


  (defn doall-simple [xs] (doall (map simple xs)))


  (defn run-simple-in-future [x]
    @(future (simple x)))


  (defn run-simple-in-thread [x]
    (.start (new Thread (fn [] (simple x))))))

(comment
  (trace/trace-ns *ns*)
  (pass-simple 1)
  (trace/untrace-ns *ns*))






(comment

  (require '[wiretap.wiretap :as wiretap]
           '[wiretap.tools :as tools])

  (defn tools-trace-clone
    [trace-id-atom {:keys [id called depth name args result]}]
    (let [pre-invocation? (= called :pre)
          trace-id (if pre-invocation? (gensym "t") (get @trace-id-atom id))
          trace-indent (apply str (take depth (repeat "| ")))
          trace-value (if pre-invocation?
                        (str trace-indent (pr-str (cons name args)))
                        (str trace-indent "=> " (pr-str result)))]
      (if pre-invocation?
        (swap! trace-id-atom assoc id trace-id)
        (swap! trace-id-atom dissoc id))
      (println (str "TRACE" (str " " trace-id) ": " trace-value))))


  (-> (partial tools-trace-clone (atom {}))
      (wiretap/install! (tools/ns-vars *ns*))))

(comment
  (def traces (atom []))

  (wiretap/install! #(swap! traces conj %) (tools/ns-vars *ns*))

  (pass-simple 1)
  (wiretap/uninstall!)
  (run! (partial tools-trace-clone (atom {})) @traces))

(comment

  (deftype Wiretap [value]
    java.lang.Object
    (toString [this] (.toString value))
    clojure.lang.ILookup
    (valAt [this at] (value at))
    clojure.lang.IFn
    (applyTo [this args] (apply value args))
    (invoke [this] (value))
    (invoke [this x1] (value x1))
    (invoke [this x1 x2] (value x1 x2))
    (invoke [this x1 x2 x3] (value x1 x2 x3))
    (invoke [this x1 x2 x3 & args]
      (println "called with:" args)
      (apply value x1 x2 x3 args)))


  (:a (let [value {:a 1}]
        (reify clojure.lang.ILookup
          (valAt [this at] (println "cool") (value at))))))

(defn sampler [var-obj]
  (let [samples (atom {})
        f (fn [{:keys [called args result error]}]
            (when (= called :post)
              (let [[ks value] (if error
                                 [[:errors args] error]
                                 [[:results args] result])]
                (swap! samples update-in ks (fnil conj #{}) value))))]
    (wiretap/install! f [var-obj])
    samples))
