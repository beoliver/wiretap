(ns wiretap.record-test
    (:require [clojure.set :as set]
              [clojure.test :refer :all]
              [wiretap.record :as rec]
              [wiretap.tools :as tools]
              [wiretap.ns-to-inspect :as ns1]
              [wiretap.other-ns-to-inspect :as ns2]))

;; Basic capture tests

(deftest capture-with-globs-test
  (testing "capture! instruments functions matching glob patterns"
    (let [cap (rec/start! {:globs ["wiretap.ns-to-inspect"]})
          _ (ns1/simple 42)
          events (rec/events cap)]
      (is (seq (:vars cap)))
      (is (seq events))
      (is (some #(and (:pre? %) (= 'simple (:name %))) events))
      (is (some #(and (:post? %) (= 'simple (:name %))) events))
      (rec/stop! cap))))

(deftest capture-with-explicit-vars-test
  (testing "capture! instruments explicitly provided vars"
    (let [cap (rec/start! {:vars [#'ns1/simple]})
          _ (ns1/simple 99)
          events (rec/events cap)]
      (is (= 1 (count (:vars cap))))
      (is (seq events))
      (is (some #(= 'simple (:name %)) events))
      (rec/stop! cap))))

(deftest capture-with-globs-and-vars-test
  (testing "capture! combines globs and explicit vars"
    (let [cap (rec/start! {:globs ["wiretap.other-ns-to-inspect"]
                           :vars [#'ns1/simple]})
          _ (ns1/simple 1)
          _ (ns2/beep 2 3)
          events (rec/events cap)
          event-names (set (map :name events))]
      (is (contains? event-names 'simple))
      (is (contains? event-names 'beep))
      (rec/stop! cap))))

;; Event structure tests

(deftest captured-events-have-required-keys-test
  (testing "captured events contain required context keys"
    (let [cap (rec/start! {:vars [#'ns1/simple]})
          _ (ns1/simple 42)
          events (rec/events cap)
          pre-event (first (filter :pre? events))
          post-event (first (filter :post? events))]
      (is pre-event "should have pre event")
      (is post-event "should have post event")
      ;; Pre event keys
      (is (:id pre-event))
      (is (:name pre-event))
      (is (:ns pre-event))
      (is (:args pre-event))
      (is (:depth pre-event))
      (is (:thread pre-event))
      (is (:start pre-event))
      (is (:pre? pre-event))
      ;; Post event keys
      (is (:id post-event))
      (is (:result post-event))
      (is (:post? post-event))
      (is (:stop post-event))
      ;; Same ID for pre and post
      (is (= (:id pre-event) (:id post-event)))
      (rec/stop! cap))))

(deftest captured-events-preserve-call-order-test
  (testing "events are captured in call order"
    (let [cap (rec/start! {:vars [#'ns1/call-simple]})
          _ (ns1/call-simple 123)
          events (rec/events cap)
          call-simple-pre (first (filter #(and (:pre? %) (= 'call-simple (:name %))) events))
          call-simple-post (first (filter #(and (:post? %) (= 'call-simple (:name %))) events))]
      (is call-simple-pre)
      (is call-simple-post)
      (is (= [123] (:args call-simple-pre)))
      (is (= 123 (:result call-simple-post)))
      (rec/stop! cap))))

;; clear! tests

(deftest clear-removes-events-test
  (testing "clear! removes all captured events"
    (let [cap (rec/start! {:vars [#'ns1/simple]})
          _ (ns1/simple 1)
          _ (ns1/simple 2)
          events-before (rec/events cap)
          _ (rec/wipe! cap)
          events-after (rec/events cap)]
      (is (seq events-before))
      (is (empty? events-after))
      (rec/stop! cap))))

(deftest clear-preserves-instrumentation-test
  (testing "clear! preserves instrumentation, new calls still captured"
    (let [cap (rec/start! {:vars [#'ns1/simple]})
          _ (ns1/simple 1)
          _ (rec/wipe! cap)
          _ (ns1/simple 2)
          events (rec/events cap)]
      (is (seq events))
      (is (some #(= [2] (:args %)) events))
      (rec/stop! cap))))

;; uninstall! tests

(deftest uninstall-removes-instrumentation-test
  (testing "uninstall! removes instrumentation from vars"
    (let [cap (rec/start! {:vars [#'ns1/simple]})
          _ (is (tools/wiretapped? #'ns1/simple))
          _ (rec/stop! cap)]
      (is (not (tools/wiretapped? #'ns1/simple))))))

(deftest uninstall-stops-capturing-test
  (testing "after uninstall!, calls are no longer captured"
    (let [cap (rec/start! {:vars [#'ns1/simple]})
          _ (ns1/simple 1)
          events-before (count (rec/events cap))
          _ (rec/stop! cap)
          _ (ns1/simple 2)
          events-after (count (rec/events cap))]
      (is (= events-before events-after)))))

;; Multiple captures

(deftest multiple-captures-independent-test
  (testing "multiple captures operate independently"
    (let [cap1 (rec/start! {:vars [#'ns1/simple]})
          cap2 (rec/start! {:vars [#'ns2/beep]})
          _ (ns1/simple 1)
          _ (ns2/beep 2 3)
          events1 (rec/events cap1)
          events2 (rec/events cap2)]
      (is (some #(= 'simple (:name %)) events1))
      (is (not-any? #(= 'beep (:name %)) events1))
      (is (some #(= 'beep (:name %)) events2))
      (is (not-any? #(= 'simple (:name %)) events2))
      (rec/stop! cap1)
      (rec/stop! cap2))))

(deftest overlapping-captures-test
  (testing "when two captures instrument same var, second one wins"
    (let [cap1 (rec/start! {:vars [#'ns1/simple]})
          cap2 (rec/start! {:vars [#'ns1/simple]})
          _ (ns1/simple 42)
          events1 (rec/events cap1)
          events2 (rec/events cap2)]
      ;; cap2 should capture the event, cap1 should not
      (is (empty? events1))
      (is (seq events2))
      (is (some #(= 'simple (:name %)) events2))
      (rec/stop! cap1)
      (rec/stop! cap2))))

;; Nested calls

(deftest nested-calls-captured-test
  (testing "nested function calls are captured with correct depth"
    (let [cap (rec/start! {:globs ["wiretap.ns-to-inspect"]})
          _ (ns1/call-simple 42)
          events (rec/events cap)
          call-simple-events (filter #(= 'call-simple (:name %)) events)
          simple-events (filter #(= 'simple (:name %)) events)]
      (is (seq call-simple-events))
      (is (seq simple-events))
      ;; simple should have greater depth than call-simple
      (let [call-simple-depth (:depth (first call-simple-events))
            simple-depth (:depth (first simple-events))]
        (is (< call-simple-depth simple-depth)))
      (rec/stop! cap))))
