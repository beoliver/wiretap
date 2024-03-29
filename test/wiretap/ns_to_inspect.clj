(ns wiretap.ns-to-inspect)

(defn simple [x] x)

(defn call-f [f x] (f x))

(defn call-simple [x] (simple x))

(defn pass-simple [x] (call-f simple x))

(defn run-simple [xs] (run! simple xs))

(defn map-simple [xs] (map simple xs))

(defn mapv-simple [xs] (mapv simple xs))

(defn doall-simple [xs] (doall (map simple xs)))

(defn run-simple-in-future [x]
  (future (simple x)))

(defn run-simple-in-future-realized [x]
  @(future (simple x)))

(defn run-simple-in-delay [x]
  (delay (simple x)))

(defn realized-run-simple-in-delay [x]
  @(delay (simple x)))

(defn run-simple-in-thread [x]
  (.start (new Thread (fn [] (simple x)))))

(defmulti my-multi (fn [{:keys [animal]}] animal))

(defmethod my-multi :cat [{:keys [name]}]
  {:the-cat name})

(defmethod my-multi :dog [{:keys [name]}]
  {:the-dog name})

