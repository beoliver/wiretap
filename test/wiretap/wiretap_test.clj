(ns wiretap.wiretap-test
  (:require [clojure.test :as test]
            [wiretap.wiretap :as wiretap]
            [wiretap.tools :as tools]
            [wiretap.ns-to-inspect :as sut]))

(def vars-of-interest
  (vals (ns-interns (find-ns 'wiretap.ns-to-inspect))))

(test/use-fixtures :each (fn [f]
                           (wiretap/uninstall! vars-of-interest)
                           (f)
                           (wiretap/uninstall! vars-of-interest)))

(test/deftest wiretapped-test
  (run! #(test/is (not (tools/wiretapped? %))) vars-of-interest)
  (let [wiretapped-vars (wiretap/install! identity vars-of-interest)]
    (run! #(test/is (tools/wiretapped? %)) wiretapped-vars)))

(defn wiretap-events [ks vars]
  (let [state (atom [])
        f (fn [{:keys [called] :as event}]
            (when ((set ks) called) (swap! state conj event)))]
    (wiretap/install! f vars)
    state))

(test/deftest call-simple-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= 1 (sut/call-simple 1)))
    (test/is (= 2 (count @state)))
    (let [[a b] @state]
      (test/is (= (:parent a) nil))
      (test/is (= (:parent b) (:id a))))))

(test/deftest pass-simple-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= 1 (sut/pass-simple 1)))
    (test/is (= 3 (count @state)))
    (let [[a b c] @state]
      (test/is (= (:parent a) nil))
      (test/is (= (:parent b) (:id a)))
      (test/is (= (:parent c) (:id b))))))

(test/deftest map-simple-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= [1 2 3] (sut/map-simple [1 2 3])))
    #_(clojure.pprint/pprint (map (fn [x] (update x :stack #(mapv clojure.repl/demunge
                                                                  (tools/filter-trace #"wiretap.*" %)))) @state))
    (test/is (= 4 (count @state)))
    (let [[a b c d] @state]
      (test/is (= (:parent a) nil))
      (test/is (= (:parent b) nil))
      (test/is (= (:parent c) nil))
      (test/is (= (:parent d) nil)))))

(test/deftest doall-simple-test
  (let [state  (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= [1 2 3] (sut/doall-simple [1 2 3])))
    #_(clojure.pprint/pprint (map (fn [x] (update x :stack #(mapv clojure.repl/demunge
                                                                  (tools/filter-trace #"wiretap.*" %)))) @state))
    (test/is (= 4 (count @state)))
    (let [[a b c d] @state]
      (test/is (= (:parent a) nil))
      (test/is (= (:parent b) (:id a)))
      (test/is (= (:parent c) (:id a)))
      (test/is (= (:parent d) (:id a))))))

(test/deftest run-simple-test
  (let [state  (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= nil (sut/run-simple [1 2 3])))
    (test/is (= 4 (count @state)))
    (let [[a b c d] @state]
      (test/is (= (:parent a) nil))
      (test/is (= (:parent b) (:id a)))
      (test/is (= (:parent c) (:id a)))
      (test/is (= (:parent d) (:id a))))))

(test/deftest binding-conveyance-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/testing "Future call preserves the `:parent` id"
      (test/is (= 1 (sut/run-simple-in-future 1)))
      (let [[a b] @state]
        (test/is (not= (:thread a) (:thread b)))
        (test/is (= (:parent a) nil))
        (test/is (= (:parent b) (:id a)))))
    (reset! state [])
    (test/testing "Thread call DOES NOT preserve the `:parent` id"
      (test/is (= nil (sut/run-simple-in-thread 1)))
      (let [[a b] @state]
        (test/is (not= (:thread a) (:thread b)))
        (test/is (= (:parent a) nil))
        (test/is (= (:parent b) nil))))))

(test/deftest listener-exceptions-test
  (let [state (atom [])]
    (wiretap/install!
     (fn [{:keys [called] :as event}]
       (case called
         :pre  (swap! state conj event)
         :post (throw (Exception. "OOPS"))))
     vars-of-interest)
    (test/is (= 1 (sut/call-simple 1)))
    (test/is (= 2 (count @state)))))


(comment
  (run! (partial ns-unmap *ns*) (keys (ns-interns *ns*))))


