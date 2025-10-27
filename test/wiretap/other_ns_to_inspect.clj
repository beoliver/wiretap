(ns wiretap.other-ns-to-inspect)

(defn beep [x y]
  (+ x y))

(defn boop [x]
  (beep x x))
