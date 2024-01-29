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
  (test/testing "a wiretap can be installed and uninstalled"
    (run! #(test/is (not (tools/wiretapped? %))) vars-of-interest)
    (let [wiretapped-vars (wiretap/install! identity vars-of-interest)]
      (run! #(test/is (tools/wiretapped? %)) wiretapped-vars)
      (wiretap/uninstall! vars-of-interest)
      (run! #(test/is (not (tools/wiretapped? %))) vars-of-interest))))

(defn wiretap-events [ks vars]
  (let [state (atom [])
        f (fn [{:keys [pre? post?] :as event}]
            (let [inculude-event? (boolean ((set ks) (cond pre? :pre post? :post)))]
              (when inculude-event? (swap! state conj event))))]
    (wiretap/install! f vars)
    state))

(test/deftest call-simple-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= 1 (sut/call-simple 1)) "expected value")
    (let [[call-simple-event _] @state]
      (test/is (= [{:name 'call-simple :parent nil}
                   {:name 'simple :parent (:id call-simple-event)}]
                  (mapv #(select-keys % [:name :parent]) @state))))))

(test/deftest pass-simple-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= 1 (sut/pass-simple 1)) "expected value")
    (let [[pass-simple-event call-f-event _] @state]
      (test/is (= [{:name 'pass-simple :parent nil}
                   {:name 'call-f :parent (:id pass-simple-event)}
                   {:name 'simple :parent (:id call-f-event)}]
                  (mapv #(select-keys % [:name :parent]) @state))))))

(test/deftest order-test
  (let [state (atom [])]
    (test/testing "pre install"
      (wiretap/install-pre! #(swap! state conj %) vars-of-interest)
      (test/is (= 1 (sut/pass-simple 1)) "expected value")
      (test/is (= ['pass-simple 'call-f 'simple] (map :name @state))))
    (test/testing "post install"
      (reset! state [])
      (wiretap/install-post! #(swap! state conj %) vars-of-interest)
      (test/is (= 1 (sut/pass-simple 1)) "expected value")
      (test/is (= ['simple 'call-f 'pass-simple] (map :name @state))))
    (test/testing "pre and post install"
      (reset! state [])
      (wiretap/install! #(swap! state conj %) vars-of-interest)
      (test/is (= 1 (sut/pass-simple 1)) "expected value")
      (test/is (= ['pass-simple 'call-f 'simple 'simple 'call-f 'pass-simple] (map :name @state))))))

(test/deftest map-simple-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= [1 2 3] (sut/map-simple [1 2 3])) "expected value")
    (test/testing "no event has parent as map is a lazy function"
      (test/is (= [{:name 'map-simple :parent nil}
                   {:name 'simple :parent nil}
                   {:name 'simple :parent nil}
                   {:name 'simple :parent nil}]
                  (mapv #(select-keys % [:name :parent]) @state))))))

(test/deftest mapv-simple-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= [1 2 3] (sut/mapv-simple [1 2 3])) "expected value")
    (test/testing "simple events have parent as mapv is not lazy"
      (let [[mapv-simple-event _a _b _c] @state]
        (test/is (= [{:name 'mapv-simple :parent nil}
                     {:name 'simple :parent (:id mapv-simple-event)}
                     {:name 'simple :parent (:id mapv-simple-event)}
                     {:name 'simple :parent (:id mapv-simple-event)}]
                    (mapv #(select-keys % [:name :parent]) @state)))))))

(test/deftest doall-simple-test
  (let [state  (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= [1 2 3] (sut/doall-simple [1 2 3])))
    (test/testing "simple events have parent as doall realizes the lazy seq"
      (let [[doall-simple-event & _] @state]
        (test/is (= [{:name 'doall-simple :parent nil}
                     {:name 'simple :parent (:id doall-simple-event)}
                     {:name 'simple :parent (:id doall-simple-event)}
                     {:name 'simple :parent (:id doall-simple-event)}]
                    (mapv #(select-keys % [:name :parent]) @state)))))))

(test/deftest run-simple-test
  (let [state  (wiretap-events #{:pre} vars-of-interest)]
    (test/is (= nil (sut/run-simple [1 2 3])))
    (test/testing "simple events have parent as run is not lazy"
      (let [[run-simple-event _a _b _c] @state]
        (test/is (= 4 (count @state)))
        (test/is (= [{:name 'run-simple :parent nil}
                     {:name 'simple :parent (:id run-simple-event)}
                     {:name 'simple :parent (:id run-simple-event)}
                     {:name 'simple :parent (:id run-simple-event)}]
                    (mapv #(select-keys % [:name :parent]) @state)))))))

(test/deftest delay-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/testing "Realizing a delay DOES NOT preserve the `:parent` id"
      ;; ie the parent is no longer on the stack...
      (let [result (sut/run-simple-in-delay 1)]
        (test/is (not (realized? result)))
        (test/is (= [{:name 'run-simple-in-delay :parent nil}]
                    (mapv #(select-keys % [:name :parent]) @state)))
        (test/is (= 1 @result))
        (test/is (= [{:name 'run-simple-in-delay :parent nil}
                     {:name 'simple :parent nil}]
                    (mapv #(select-keys % [:name :parent]) @state)))))
    (reset! state [])
    (test/testing "If the delay was realized inside parent then parent preserved"
      (let [result (sut/realized-run-simple-in-delay 1)]
        (test/is (= 1 result))
        (let [[realized-run-simple-in-delay-event & _simple-events] @state]
          (test/is (= [{:name 'realized-run-simple-in-delay :parent nil}
                       {:name 'simple :parent (:id realized-run-simple-in-delay-event)}]
                      (mapv #(select-keys % [:name :parent]) @state))))))))

(test/deftest binding-conveyance-test
  (let [state (wiretap-events #{:pre} vars-of-interest)]
    (test/testing "TODO: does a Future call preserve the `:parent` id if it not realized?"
      (let [result (sut/run-simple-in-future 1)]
        (test/is (future? result))
        (let [derefed @result
              [a b] @state]
          (test/is (= 1 derefed))
          (test/is (not= (:thread a) (:thread b)))
          (test/is (= (:parent a) nil))
          (test/is (= (:parent b) (:id a))))))
    (reset! state [])
    (test/testing "Realized future call preserves the `:parent` id"
      (test/is (= 1 (sut/run-simple-in-future-realized 1)))
      (let [[a b] @state]
        (test/is (not= (:thread a) (:thread b)))
        (test/is (= (:parent a) nil))
        (test/is (= (:parent b) (:id a)))))
    (reset! state [])
    (test/testing "Thread call DOES NOT preserve the `:parent` id."
      ;; this test might fail or break other tests when run on a machine with a single CPU.
      (test/is (= nil (sut/run-simple-in-thread 1)))
      (let [[a b] @state]
        (test/is (not= (:thread a) (:thread b)))
        (test/is (= (:parent a) nil))
        (test/is (= (:parent b) nil))))))

(test/deftest listener-exceptions-test
  (let [state (atom [])]
    (wiretap/install!
     (fn [{:keys [pre? post?] :as event}]
       (cond
         pre?  (swap! state conj event)
         post? (throw (Exception. "OOPS"))))
     vars-of-interest)
    (test/is (= 1 (sut/call-simple 1)))
    (test/is (= 2 (count @state)))))

(test/deftest multimethod-test
  (let [state (atom [])]
    (wiretap/install-pre! #(swap! state conj %) [#'sut/my-multi])
    (test/is (= {:the-cat "felix"} (sut/my-multi {:animal :cat :name "felix"})))
    (test/is (= [{:name 'my-multi :multimethod? true :dispatch-val :cat}]
                (mapv #(select-keys % [:name :multimethod? :dispatch-val]) @state)))
    (reset! state [])
    (test/is (= {:the-dog "rover"} (sut/my-multi {:animal :dog :name "rover"})))
    (test/is (= [{:name 'my-multi :multimethod? true :dispatch-val :dog}]
                (mapv #(select-keys % [:name :multimethod? :dispatch-val]) @state)))))

(comment
  (run! (partial ns-unmap *ns*) (keys (ns-interns *ns*))))
