# wiretap
#### A Clojure library for adding generic trace support without having to modify code.

</br>

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

 Given a [var](https://clojure.org/reference/vars) whose value implements Fn, i.e was created by fn - wiretap lets you `install!` a side effecting function `f` that will be called both **pre** and **post** invocation of the var's original value.
 
This pattern captures the _essence_ of a trace. By allowing a custom function `f`, wiretap can be used for multiple different purposes.

 # API

 ## `wiretap.wiretap/install!`
 ```clojure
 (install! [f vars])
 ```

For every applicable var in vars - removes any existing wiretap and alters
the root binding to be a variadic function closing over the value `g` of the 
var and the user provided function `f`.

> A var is considered applicable if its metadata does not contain the 
> key `:wiretap.wiretap/exclude` and its value implements Fn, i.e. is an 
> object created via `fn`.

When the resulting "wiretapped" function is called, a map representing the 
**context** of the call is first passed to `f` before the result is computed 
by applying `g` to to any args provided. `f` is then called with an updated 
context before the result is returned. In both cases, `f` is executed within 
a `try/catch` on the same thread. The result of calling `f` is discarded.

Returns a coll of all modified vars.

The following contextual data is will **always** be present in the map passed 
to `f`:

| Key         | Value                                                            |
| ----------- | ---------------------------------------------------------------- |
| `:id`       | Uniquely identifies the call. Same value for pre and post calls. |
| `:name`     | A symbol. Taken from the _meta_ of the var.                      |
| `:ns`       | A namespace. Taken from the _meta_ of the var.                   |
| `:function` | The value that will be applied to the value of `:args`.          |
| `:thread`   | The name of the thread.                                          |
| `:stack`    | The current stacktrace.                                          |
| `:depth`    | Number of _wiretapped_ function calls on the stack.              |
| `:args`     | The seq of args that value of `:function` will be applied to.    |
| `:start`    | Nanoseconds since some fixed but arbitrary origin time.          |
| `:parent`   | The context of the previous wiretapped function on the stack.    |

### Pre invocation

When `f` is called **pre** invocation the following information will also be present.
| Key     | Value                                                                  |
| ------- | ---------------------------------------------------------------------- |
| `:pre?` | `true` |

### Post invocation

When `f` is called **post** invocation the following information will also be present.

| Key       | Value                                                                             |
| --------- | --------------------------------------------------------------------------------- |
| `:post?`  | `true`           |
| `:stop`   | Nanoseconds since some fixed but arbitrary origin time.                           |
| `:result` | The result computed by applying the value of `:function` to the value of `:args`. |
| `:error`  | Any exception caught during computation of the result.                            |



 ## `wiretap.wiretap/uninstall!`
 
 ```clojure
 (uninstall! ([]) ([vars]))
 ```

Sets the root binding of every applicable var to a be the value before calling
   `install!`. If called without any arguments then all vars in namespaces available
   via `clojure.core/all-ns` will be checked.

> A var is considered applicable if a valid value is present under the metadata key `:wiretap.wiretap/wiretapped` and its metadata does not contain the key `:wiretap.wiretap/exclude`.

   Returns a coll of all modified vars.

 ## `wiretap.tools/ns-matches-vars`

 Given an instance of `java.util.regex.Pattern`, returns a seq of all vars that have been interned in namespaces matched by the regex.

# Examples
## Writing a tools.trace clone

Assume that we have the following namespace definitions...
```clojure
(ns user)

(defn simple [x] (inc x))

(defn call-f [f x] (f x))

(defn pass-simple [x] (call-f simple x))
```
To show how wiretap events can be used - we will generate traces similar to those of the [`clojure/tools.trace`](https://github.com/clojure/tools.trace) library. All we need to do is write a function that can take a wiretap context map and perform some io (call to `println`).
```clojure
(defn ^:wiretap.wiretap/exclude my-trace
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
```
To make things interesting - we will **persist** all of the contexts and then run our trace function on the data. Repeatable traces!
```clojure
user=> (def history (atom []))
#'user/history
user=> (wiretap/install! #(swap! history conj %) (tools/ns-vars *ns*))
(#'user/pass-simple #'user/simple #'user/call-f)
user=> (pass-simple 1)
2
user=> (count @history)
6
user=> (run! (partial my-trace (atom {})) @history)
TRACE t8018: (user/pass-simple 1)
TRACE t8019: | (user/call-f #function[clojure.lang.AFunction/1] 1)
TRACE t8020: | | (user/simple 1)
TRACE t8020: | | => 2
TRACE t8019: | => 2
TRACE t8018: => 2
nil
```


## Sampling data

```clojure
(defn sampler [var-obj]
  (let [samples (atom {})
        f (fn [{:keys [post? args result error]}]
            (when post?
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

# Related

- https://github.com/clojure/tools.trace
- https://github.com/circleci/bond
- https://github.com/alexanderjamesking/spy
- https://github.com/technomancy/robert-hooke

# Development

```
clj -X:test
```
