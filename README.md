# wiretap

<img src="./wiretap.jpg" title="wiretap" width="50%" align="right"/>

> **wiretap** | ˈwʌɪətap |
>
> [**noun**]
> 
>  A concealed device connected to a telephone or other communications system that allows a third party to listen or record conversations.
> 
> [**verb**]
> 
> To install or to use such a device.

 Given a clojure [var](https://clojure.org/reference/vars) whose value (content) implements Fn, i.e was created by fn - wiretap lets you `install!` a custom function that will be passed a map of _contextual_ data (and whose return value is ignored) both **pre** and **post** invocation of the original var value. In essence this is how a _trace_ is calculated. By allowing the user to provide their own function, wiretap can be used for multiple different purposes.


<br/>


```clojure
(ns user)

(defn simple [x] (inc x))

(defn call-f [f x] (f x))

(defn pass-simple [x] (call-f simple x))
```
## Writing a tools.trace clone
To show how wiretap events can be used - we will write a simple tracer that mimics some of the functionality found in the  [`tools.trace`](https://github.com/clojure/tools.trace) library. However, unlike `tools.trace`, we will persist the data required to _display_ the trace.
```clojure
user=> (require '[clojure.tools.trace :as trace])
nil
user=> (trace/trace-ns *ns*)
nil
user=> (pass-simple 1)
TRACE t8778: (user/pass-simple 1)
TRACE t8779: | (user/call-f #function[clojure.tools.trace/trace-var*/fn--6172/tracing-wrapper--6173] 1)
TRACE t8780: | | (user/simple 1)
TRACE t8780: | | => 2
TRACE t8779: | => 2
TRACE t8778: => 2
2
user=> (trace/untrace-ns *ns*)
nil
user=> (pass-simple 1)
2
```
We write a function that can take a wiretap event map and perform some io. The first parameter will just be an atom that we use to map the event ids onto the kind of trace id that we want to print. 
```clojure
(defn ^:wiretap.wiretap/exclude my-trace
  [trace-ids {:keys [id called depth name args result] :as wiretap-event}]
  (let [pre-invocation? (= called :pre)
        trace-id (if pre-invocation? (gensym "t") (get @trace-ids id))
        trace-indent (apply str (take depth (repeat "| ")))
        trace-value (if pre-invocation?
                      (str trace-indent (pr-str (cons name args)))
                      (str trace-indent "=> " (pr-str result)))]
    (if pre-invocation?
      (swap! trace-ids assoc id trace-id)
      (swap! trace-ids dissoc id))
    (println (str "TRACE" (str " " trace-id) ": " trace-value))))
```
Now that we have out trace function that can take wiretap events and produce a `tools.trace` style output, we can take it for a spin. For the sake of example, let us **persist** all events and then run print a trace of the persisted events.
```clojure
user=> (def events (atom []))
#'user/events
user=> (wiretap/install! #(swap! events conj %) (tools/ns-vars *ns*))
(#'user/pass-simple #'user/simple #'user/call-f)
user=> (pass-simple 1)
2
user=> (count @events)
6
user=> (wiretap/uninstall!)
(#'user/pass-simple #'user/simple #'user/call-f)
user=> (pass-simple 2)
3
user=> (count @events)
6
```
Now that we have some data, let's try to trace it! If we didn't care about persisting the events, then we could just pass the partially applied `my-trace` to `install!` and get _live_ tracing.
```clojure
user=> (run! (partial my-trace (atom {})) @events)
TRACE t9119: (pass-simple 1)
TRACE t9120: | (call-f #function[clojure.lang.AFunction/1] 1)
TRACE t9121: | | (simple 1)
TRACE t9121: | | => 2
TRACE t9120: | => 2
TRACE t9119: => 2
nil
```


# Sampling data

```clojure
(defn sampler [var-obj]
  (let [samples (atom {})
        f (fn [{:keys [called args result error]}]
            (when (= called :post)
              (let [[ks value] (if error
                                 [[:errors args] error]
                                 [[:results args] result])]
                (swap! samples update-in ks (fnil conj #{}) value))))]
    (wiretap/install! f [var-obj])
    samples))
```
```clojure
user=> (def call-f-samples (sampler #'call-f))
#'user/call-f-samples
user=> call-f-samples
#<Atom@18142d69: {}>
user=> (call-f inc 2)
3
user=> (call-f dec 2)
1
user=> (call-f + 2)
2
user=> (call-f :a {:a 1})
1
user=> @call-f-samples
{:results
 {(#function[clojure.core/inc] 2) #{3},
  (#function[clojure.core/dec] 2) #{1},
  (#function[clojure.core/+] 2) #{2},
  (:a {:a 1}) #{1}}}
user=> (call-f inc "2")
; Execution error (ClassCastException) at user/call-f (user.clj:9).
user=> (wiretap/uninstall! [#'call-f])
(#'user/call-f)
user=> (.getMessage (first (get-in @call-f-samples [:errors [inc "2"]])))
"class java.lang.String cannot be cast to class java.lang.Number (java.lang.String and java.lang.Number are in module java.base of loader 'bootstrap')"
```

## Related

- https://github.com/clojure/tools.trace
- https://github.com/circleci/bond
- https://github.com/alexanderjamesking/spy

## Development

```
clj -X:test
```
