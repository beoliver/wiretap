(ns wiretap.tools-test
    (:require [clojure.set :as set]
              [clojure.test :refer :all]
              [wiretap.tools :as tools]
              [wiretap.ns-to-inspect]
              [wiretap.other-ns-to-inspect]))

;; glob-regex tests

(deftest glob-regex-literal-test
  (testing "literal segments match exactly"
    (let [pattern (tools/glob-regex "foo.bar.baz")]
      (is (re-matches pattern "foo.bar.baz"))
      (is (nil? (re-matches pattern "foo.bar.qux")))
      (is (nil? (re-matches pattern "foo.bar"))))))

(deftest glob-regex-single-wildcard-test
  (testing "* matches exactly one segment"
    (let [pattern (tools/glob-regex "foo.*.baz")]
      (is (re-matches pattern "foo.bar.baz"))
      (is (re-matches pattern "foo.qux.baz"))
      (is (nil? (re-matches pattern "foo.baz")))
      (is (nil? (re-matches pattern "foo.bar.qux.baz"))))))

(deftest glob-regex-double-wildcard-test
  (testing "** matches zero or more segments"
    (let [pattern (tools/glob-regex "foo.**")]
      (is (re-matches pattern "foo"))
      (is (re-matches pattern "foo.bar"))
      (is (re-matches pattern "foo.bar.baz"))
      (is (re-matches pattern "foo.bar.baz.qux"))
      (is (nil? (re-matches pattern "other.bar"))))))

(deftest glob-regex-double-wildcard-middle-test
  (testing "** in the middle matches zero or more segments"
    (let [pattern (tools/glob-regex "foo.**.baz")]
      (is (re-matches pattern "foo.baz"))
      (is (re-matches pattern "foo.bar.baz"))
      (is (re-matches pattern "foo.x.y.z.baz"))
      (is (nil? (re-matches pattern "foo.bar"))))))

(deftest glob-regex-mixed-wildcards-test
  (testing "combination of * and ** wildcards"
    (let [pattern (tools/glob-regex "foo.*.**.baz")]
      (is (re-matches pattern "foo.bar.baz"))
      (is (re-matches pattern "foo.bar.x.y.baz"))
      (is (nil? (re-matches pattern "foo.baz"))))))

;; ns-matches tests

(deftest ns-matches-finds-test-namespaces
  (testing "ns-matches finds loaded test namespaces"
    (let [pattern (re-pattern "wiretap\\..*-to-inspect")
          matches (set (tools/ns-matches pattern))]
      (is (contains? matches 'wiretap.ns-to-inspect))
      (is (contains? matches 'wiretap.other-ns-to-inspect)))))

(deftest ns-matches-with-glob-pattern
  (testing "ns-matches works with glob-regex generated patterns"
    (let [matches (set (tools/ns-matches (tools/glob-regex "wiretap.**")))]
      (is (set/subset? #{'wiretap.ns-to-inspect
                         'wiretap.other-ns-to-inspect}
                       matches)))))

(deftest ns-matches-returns-symbols
  (testing "ns-matches returns symbols not strings or namespace objects"
    (let [matches (tools/ns-matches (tools/glob-regex "wiretap.**"))]
      (is (every? symbol? matches)))))

;; ns-vars tests

(deftest ns-vars-basic-test
  (testing "ns-vars returns vars from a single namespace"
    (let [vars (tools/ns-vars 'wiretap.ns-to-inspect)]
      (is (seq vars))
      (is (every? var? vars))
      (is (some #(= 'simple (:name (meta %))) vars)))))

(deftest ns-vars-multiple-namespaces-test
  (testing "ns-vars combines vars from multiple namespaces"
    (let [vars (set (tools/ns-vars 'wiretap.ns-to-inspect 'wiretap.other-ns-to-inspect))
          var-names (set (map #(:name (meta %)) vars))]
      (is (contains? var-names 'simple))
      (is (contains? var-names 'beep))
      (is (contains? var-names 'boop)))))

(deftest ns-vars-deduplicates-test
  (testing "ns-vars deduplicates when same namespace passed multiple times"
    (let [vars1 (tools/ns-vars 'wiretap.ns-to-inspect 'wiretap.ns-to-inspect)
          vars2 (tools/ns-vars 'wiretap.ns-to-inspect)]
      (is (= (set vars1) (set vars2))))))

;; ns-matches-vars tests

(deftest ns-matches-vars-test
  (testing "ns-matches-vars combines ns-matches and ns-vars"
    (let [vars (tools/ns-matches-vars (re-pattern "wiretap\\.ns-to-inspect"))
          var-names (set (map #(:name (meta %)) vars))]
      (is (contains? var-names 'simple))
      (is (contains? var-names 'call-simple)))))

;; globs-vars tests

(deftest globs-vars-single-glob-test
  (testing "globs-vars works with single glob pattern"
    (let [vars (tools/globs-vars ["wiretap.ns-to-inspect"])
          var-names (set (map #(:name (meta %)) vars))]
      (is (contains? var-names 'simple)))))

(deftest globs-vars-multiple-globs-test
  (testing "globs-vars combines results from multiple glob patterns"
    (let [vars (tools/globs-vars ["wiretap.ns-to-inspect" "wiretap.other-ns-to-inspect"])
          var-names (set (map #(:name (meta %)) vars))]
      (is (contains? var-names 'simple))
      (is (contains? var-names 'beep))
      (is (contains? var-names 'boop)))))

(deftest globs-vars-double-wildcard-test
  (testing "globs-vars works with ** wildcard"
    (let [vars (tools/globs-vars ["wiretap.**"])
          var-names (set (map #(:name (meta %)) vars))]
      (is (contains? var-names 'simple))
      (is (contains? var-names 'beep)))))

(deftest globs-vars-returns-vector
  (testing "globs-vars returns a vector"
    (let [result (tools/globs-vars ["wiretap.ns-to-inspect"])]
      (is (vector? result)))))
