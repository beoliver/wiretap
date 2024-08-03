(ns wiretap-server.logging
  (:require [taoensso.timbre :as timbre]))

(def the-out *out*)

(defmacro info [& args]
  `(binding [*out* ~the-out]
     (timbre/info ~@args)))

(defmacro error [& args]
  `(binding [*out* ~the-out]
     (timbre/error ~@args)))