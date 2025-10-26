(ns wiretap.history
  "Provides functions for capturing and displaying the history of calls traced by
   the wiretap library. This includes utilities for matching namespaces,
   installing a history-capturing tracer, and displaying the captured history."
  (:require [wiretap.wiretap :as wiretap]))

(defn- ^:wiretap.wiretap/exclude basic-trace
  "A basic, internal tracer function for printing call trace information
   to the console. It handles indentation and associates temporary IDs
   with function calls to link pre-call (entry) and post-call (exit) events.

  This function is primarily used internally by `display-trace`."
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

(defrecord HistoryCapture [vars history])

(defn ^:wiretap.wiretap/exclude match-namespaces
  "Given a sequence of patterns (strings or regexes), returns a set of namespace
   names (strings) whose full names match any of the patterns.

  Patterns can be literal strings (treated as exact regexes) or java.util.regex.Pattern objects."
  [patterns]
  (let [all-ns (map #(.toString %) (all-ns))]
    (reduce (fn [acc pattern]
              (let [regex (if (string? pattern)
                            (re-pattern pattern)
                            pattern)
                    matched-ns (filter #(re-matches regex %) all-ns)]
                (into acc matched-ns)))
            #{}
            patterns)))

(defn ^:wiretap.wiretap/exclude match-namespace
  "Given a single pattern (string or regex), returns a set of namespace
   names (strings) whose full names matches the pattern.

  This is a convenience wrapper around `match-namespaces`."
  [pattern]
  (match-namespaces [pattern]))

(defn ^:wiretap.wiretap/exclude capture!
  "Installs a tracer function on a set of Vars derived from the provided options.
   This tracer captures all call/return context maps into an internal history
   atom.

  Options map keys:
  `:vars` - A sequence of Vars to explicitly instrument.
  `:namespaces` - A sequence of namespace objects or symbols/strings naming
                  namespaces whose public functions should be instrumented.
  `:patterns` - A sequence of regexes or strings used to match and instrument
                Vars in namespaces whose names match the patterns.

  Returns a `HistoryCapture` record containing the set of modified Vars and the history atom."
  [{:keys [vars namespaces patterns] :as options}]
  (let [all-namespaces (into (set namespaces)
                             (match-namespaces patterns))
        possible-vars (into (set vars)
                            (comp (keep (fn [x]
                                          (cond
                                            (string? x) (the-ns (symbol x))
                                            (symbol? x) (the-ns x)
                                            :else (the-ns x))))
                                  (map ns-interns)
                                  (map vals)
                                  cat) all-namespaces)
        history (atom [])
        modified-vars (wiretap/install! #(swap! history conj %) possible-vars)]
    (map->HistoryCapture {:vars modified-vars :history history})))

(defn ^:wiretap.wiretap/exclude history
  "Retrieves the sequence of captured trace context maps from a `HistoryCapture`
   record.

  Returns a vector of context maps (the dereferenced value of the history atom)."
  [{:keys [history] :as capture}]
  @history)

(defn ^:wiretap.wiretap/exclude clear!
  "Clears the captured history in the `HistoryCapture` record."
  [{:keys [history] :as capture}]
  (reset! history []))

(defn ^:wiretap.wiretap/exclude display
  "Prints the captured call history from a `HistoryCapture` record to the console
   using the default display format (`display-trace`)."
  [{:keys [history] :as capture}]
  (display-trace @history))

(defn ^:wiretap.wiretap/exclude uninstall!
  "Removes the installed tracer from all Vars in the `HistoryCapture` record,
   restoring them to their original state."
  [{:keys [vars] :as capture}]
  (wiretap/uninstall! vars))

