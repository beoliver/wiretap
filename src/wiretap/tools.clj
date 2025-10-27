(ns wiretap.tools
    (:require [clojure.string :as str]))

(defn ^:wiretap.wiretap/exclude ns-vars [& namespaces]
  (into []
        (mapcat #(vals (ns-interns %)))
        (set namespaces)))

(defn ^:wiretap.wiretap/exclude ns-matches
  "Given an instance of java.util.regex.Pattern, returns a vector of symbols
   naming the currently loaded libs that are matched by `regex`."
  {:doc/format :markdown}
  [regex]
  (into []
        (comp
          (map #(.getName %))
          (filter
            (comp (partial re-matches regex) name)))
        (all-ns)))

(defn ^:wiretap.wiretap/exclude ns-matches-vars
  "Given an instance of java.util.regex.Pattern, returns a vector of all vars
   that have been interned in namespaces matched by the regex"
  {:doc/format :markdown}
  [regex]
  (apply ns-vars (ns-matches regex)))

(defn ^:wiretap.wiretap/exclude wiretapped? [var-obj]
  (boolean (:wiretap.wiretap/uninstall (meta var-obj))))

(defn ^:wiretap.wiretap/exclude filter-trace [pattern trace]
  (->> (map #(.getClassName %) trace)
       (keep #(re-matches pattern %))))

(defn ^:wiretap.wiretap/exclude glob-regex
  "Converts a namespace glob pattern into a compiled regex Pattern.

  Glob syntax:
  - `*` matches a single namespace segment (e.g., 'foo.*.bar' matches 'foo.baz.bar')
  - `**` matches zero or more segments (e.g., 'foo.**' matches 'foo.bar.baz')
  - Literal segments match exactly

  Returns a java.util.regex.Pattern suitable for matching namespace names."

  [glob-pattern]
  (let [d "\\."
        g "[^\\.]*"
        gs "(\\..*?)*"]
    (->>
      (reduce (fn [path segment]
                (let [prev (last path)
                      next-segments
                      (cond
                        (nil? prev) [(case segment
                                       "*" g
                                       "**" gs
                                       segment)]
                        (= prev g) (case segment
                                     "*" [d g]
                                     "**" [gs]
                                     [d segment])
                        (= prev gs) (case segment
                                      ("*" "**") [] ;; consumed by the previous **
                                      [d segment])
                        :else (case segment
                                "*" [d g]
                                "**" [gs]
                                [d segment]))]
                  (into path next-segments)))
              []
              (str/split glob-pattern #"\."))
      (str/join)
      (format "^%s$")
      (re-pattern))))

(defn ^:wiretap.wiretap/exclude globs-vars
  "Given a collection of glob patterns, returns a vector of all matching vars.

  Example: (globs-vars [\"foo.**.bar\" \"foo.*.baz\"])"
  [glob-patterns]
  (into []
        (comp (map glob-regex)
              (mapcat ns-matches-vars))
        glob-patterns))

(defn- ^:wiretap.wiretap/exclude basic-trace
  "A basic, internal tracer function for printing call trace information
   to the console. It handles indentation and associates temporary IDs
   with function calls to link pre-call (entry) and post-call (exit) events."
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

(defn ^:wiretap.wiretap/exclude display-trace
  "Prints a sequence of trace events (history) to the console using a
   basic-trace format.

  `history` is expected to be a sequence of wiretap context maps."
  [history]
  (run! (partial basic-trace (atom {})) history))
