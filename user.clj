(ns user
  (:require
   [wiretap.wiretap :as wiretap]
   [wiretap.record :as rec]
   [wiretap.tools :as tools]))

(defn foo [x] (inc x))

(defn bar [x] (foo x))




(def recording (rec/start! {:globs ["wiretap.ns-to-inspect"]}))