(ns wiretap.record
    "Provides functions for recording and playing back events traced by
     the wiretap library. This includes utilities for matching namespaces,
     installing a basic event-recording tracer, and displaying the recorded events."
  (:require [wiretap.wiretap :as wiretap]
            [wiretap.tools :as tools]))

(defn ^:wiretap.wiretap/exclude start!
  "Installs a tracer function on a set of Vars derived from the provided options.
   This tracer records all call/return context maps into an events atom.

  Options map keys:
  `:vars` - A sequence of Vars to explicitly instrument.
  `:globs` - A sequence of strings that use the glob patterns * and ** to match against
             namespaces - eg \"my-proj.**\" and \"my-proj.*.bar.**.baz\"

  Returns a map with keys :vars and :events containing the wiretapped vars and events atom"
  [{:keys [vars globs] :as options}]
  (let [possible-vars (into (set vars) (tools/globs-vars globs))
        events (atom [])
        modified-vars (wiretap/install! #(swap! events conj %) possible-vars)]
    {:vars modified-vars :events events}))

(defn ^:wiretap.wiretap/exclude events
  "Returns a vector of all recorded trace events.

  See `wiretap.wiretap/install!` for full documentation of event context maps."
  [{:keys [events] :as recorder}]
  @events)

(defn ^:wiretap.wiretap/exclude wipe!
  "Clears all recorded trace events from the recording.

  The wiretap remains installed and will continue recording new events."
  [{:keys [events] :as recorder}]
  (reset! events []))

(defn ^:wiretap.wiretap/exclude playback
  "Prints recorded trace events to the console in a formatted, indented style.

  Shows function calls with arguments on entry and results on exit."
  [{:keys [events] :as recorder}]
  (tools/display-trace @events))

(defn ^:wiretap.wiretap/exclude stop!
  "Removes the wiretap from all instrumented vars, restoring their original behavior.

  After uninstalling, the vars will no longer record trace events. The existing
  recorded events remain accessible until cleared."
  [{:keys [vars] :as recorder}]
  (wiretap/uninstall! vars))
