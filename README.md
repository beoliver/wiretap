# wiretap
[![Clojars Project](https://img.shields.io/clojars/v/io.github.beoliver/wiretap.svg)](https://clojars.org/io.github.beoliver/wiretap)

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

This library provides a small set of tools that help you to observe the execution of functions and multimethods. It is designed to be used in (dev) environments where you want to gain insights without having to modify/annotate code.

Any [var](https://clojure.org/reference/vars) whose value is an instance of Fn or MultiFn (i.e was created via [`fn`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/fn) or [`defmulti`](https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/defmulti)) can be wiretapped. 

As a user of the library, you provide a function that will be called before and/or after any wiretapped function or method is invoked. This function can be used to perform any side effecting operations - for example swapping values in an atom, or simply calling `println`. This custom function is passed a context map that contains information about the invocation of the var - the values vary depending on whether the function is called before or after the invocation. For more information see [wiretap context](#wiretap-context).

This pre/post pattern captures the essence of a trace, however wiretap can be used for multiple different purposes.

# Releases

As a git dep:
```clojure
io.github.beoliver/wiretap {:git/sha "ba28311"}
```
As a Maven dep:
```clojure
io.github.beoliver/wiretap {:mvn/version "0.0.10"}
```

# A Simple Example

```clojure
(ns user)
(defn foo [x] (inc x))
(defn bar [x] (foo (dec x)))
```

```clojure
user=> (require '[wiretap.wiretap :as wiretap])
nil
user=> (wiretap/install! #(println (if (:pre? %) ">" "<") (:name %)) [#'foo #'bar])
(#'user/foo #'user/bar)
user=> (+ 10 (bar 1))
> bar
> foo
< foo
< bar
11
```


# Supported Types

Depending on the type of the var value, different strategies are used to wrap the invocation of the var. The following table shows the supported types and the corresponding wrapping strategy.

| Instance | wiretap |
| -------- | ---------- |
| `Fn`     | Wraps invocation of the function |
| `MultiFn` | Wraps invocation of the selected method determined by the dispatch-value |

# Wiretap Context

Depending on whether the user provided function is called before or after the invocation of the wiretapped var, the context map will contain different information. The following table shows the keys that will be present in the context map and when they will be present.

| Key         | When     | Value                                                            |
| ----------- | -------- | ---------------------------------------------------------------- |
| `:id`       | pre/post | Uniquely identifies the call. Same value for pre and post calls. |
| `:name`     | pre/post | A symbol. Taken from the _meta_ of the var.                      |
| `:ns`       | pre/post | A namespace. Taken from the _meta_ of the var.                   |
| `:function` | pre/post | The value that will be applied to the value of `:args`.          |
| `:thread`   | pre/post | The name of the thread.                                          |
| `:stack`    | pre/post | The current stacktrace.                                          |
| `:depth`    | pre/post | Number of _wiretapped_ function calls on the stack.              |
| `:args`     | pre/post | The seq of args that value of `:function` will be applied to.    |
| `:start`    | pre/post | Nanoseconds since some fixed but arbitrary origin time.          |
| `:parent`   | pre/post | The context of the previous wiretapped function on the stack.    |
| `:pre?`     | pre      | `true`                                                           |
| `:post?`    | post     | `true`                                                           |
| `:stop`     | post     | Nanoseconds since some fixed but arbitrary origin time.          |
| `:result`   | post     | The result of applying the value of `:function` to `:args`.      |
| `:error`    | post     | Any exception caught during computation of the result.           |

If the wiretapped var is a multimethod then the following information will also be present.

| Key              | When     | Value                                                       |
| ---------------- | ---------| ----------------------------------------------------------- |
| `:multimethod?`  | pre/post | `true`                                                      |
| `:dispatch-val`  | pre/post | The dispatch value used to select the method.               |

# API

Documentation hosted [here](https://beoliver.github.io/wiretap/index.html)

# Examples


## Multimethods

Assume that we have a multimethod

```clojure
(defmulti m1 :name)

(defmethod m1 :foo [x] {:the-foo x})
(defmethod m1 :bar [x] {:the-bar x})
```

We can wiretap this as follows

```clojure
user=> (wiretap/install!
        #(when (:pre? %)
          (println (select-keys % [:name :dispatch-val :args]))) [#'m1])
(#'user/m1)
user=> (m1 {:name :foo})
{:name m1, :dispatch-val :foo, :args ({:name :foo})} ;; printed line
{:the-foo {:name :foo}}
user=> (m1 {:name :bar})
{:name m1, :dispatch-val :bar, :args ({:name :bar})}  ;; printed line
{:the-bar {:name :bar}}
```
However, if we add a new method then it will **not** be wiretapped.
```clojure
user=> (defmethod m1 :baz [x] {:the-baz x})
#multifn[m1 0x2f3911be]
user=> (m1 {:name :baz})
{:the-baz {:name :baz}}
```
The methods that were wiretapped, remain wiretapped.
```clojure
user=> (m1 {:name :foo})
{:name m1, :dispatch-val :foo, :args ({:name :foo})} ;; printed line
{:the-foo {:name :foo}}
```
When uninstalling, the wiretapped methods are replaced with the original ones.
```clojure
user=> (wiretap/uninstall! [#'m1])
(#'user/m1)
user=> (m1 {:name :foo})
{:the-foo {:name :foo}}
user=> (m1 {:name :bar})
{:the-bar {:name :bar}}
user=> (m1 {:name :baz})
{:the-baz {:name :baz}}
```


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


## Inferring specs
Now that we have a _history_ of events, we can perform other operations on them! In the previous example we called `pass-simple` passing the value `1`. Let's use the [spec-provider](https://github.com/stathissideris/spec-provider) library to _infer_ some specs from the trace.
```clojure
(require '[spec-provider.provider :as sp])

(defn result-spec [history var-obj]
  (let [var-ns (:ns (meta var-obj))
        var-name (:name (meta var-obj))
        examples (->> history
                      (filter (fn [{:keys [post? error ns name]}]
                                (and post?
                                     (nil? error) ;; ignore results if error thrown
                                     (= ns var-ns)
                                     (= name var-name))))
                      (map :result))]
    (sp/pprint-specs
     (sp/infer-specs (set examples) (keyword (name (ns-name var-ns))
                                             (name var-name)))
     var-ns 'spec)))
```
We can now use the function to infer the spec of the _return_ value for a function - even if we never called it directly.
```clojure
=> (return-spec @history #'simple)
(spec/def ::simple integer?)
=> (call-f simple 2.0)
3.0
=> (return-spec @history #'simple)
(spec/def ::simple (spec/or :double double? :integer integer?))
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

# Test in the REPL

```
clj -Sdeps '{:deps {wiretap/wiretap {:git/url "https://github.com/beoliver/wiretap/" :git/sha "de8814d6d46eed26f15c3878e59927552eee904c"}}}' -e "(require '[wiretap.wiretap :as wiretap] '[wiretap.tools :as wiretap-tools])" -r
```

```clojure
Checking out: https://github.com/beoliver/wiretap/ at de8814d6d46eed26f15c3878e59927552eee904c
WARNING: Implicit use of clojure.main with options is deprecated, use -M
user=> (def foo (fn [x] (+ x x)))
#'user/foo
user=> (wiretap/install! #(when (:post? %) (clojure.pprint/pprint %)) [#'foo])
(#'user/foo)
user=> (foo 10)
{:args (10),
 :parent nil,
 :ns #object[clojure.lang.Namespace 0x309028af "user"],
 :name foo,
 :start 298028669941875,
 :function #object[user$foo 0x44841b43 "user$foo@44841b43"],
 :stop 298028670197791,
 :result 20,
 :thread "main",
 :post? true,
 :id "7bd32775-d675-48ab-8d42-c56924ed7ee3",
 :stack
 [[java.lang.Thread getStackTrace "Thread.java" 1602],
  [wiretap.wiretap$wiretap_var_BANG_$wiretapped__149 doInvoke "wiretap.clj" 17],
  [clojure.lang.RestFn applyTo "RestFn.java" 137],
  [clojure.lang.AFunction$1 doInvoke "AFunction.java" 31],
  [clojure.lang.RestFn invoke "RestFn.java" 408],
  [user$eval223 invokeStatic "NO_SOURCE_FILE" 1],
  [user$eval223 invoke "NO_SOURCE_FILE" 1],
  [clojure.lang.Compiler eval "Compiler.java" 7194],
  [clojure.lang.Compiler eval "Compiler.java" 7149],
  [clojure.core$eval invokeStatic "core.clj" 3215],
  [clojure.core$eval invoke "core.clj" 3211],
  [clojure.main$repl$read_eval_print__9206$fn__9209 invoke "main.clj" 437],
  [clojure.main$repl$read_eval_print__9206 invoke "main.clj" 437],
  [clojure.main$repl$fn__9215 invoke "main.clj" 458],
  [clojure.main$repl invokeStatic "main.clj" 458],
  [clojure.main$repl_opt invokeStatic "main.clj" 522],
  [clojure.main$repl_opt invoke "main.clj" 518],
  [clojure.main$main invokeStatic "main.clj" 664],
  [clojure.main$main doInvoke "main.clj" 616],
  [clojure.lang.RestFn applyTo "RestFn.java" 137],
  [clojure.lang.Var applyTo "Var.java" 705],
  [clojure.main main "main.java" 40]],
 :depth 0}
20
user=> (wiretap/uninstall!)
(#'user/foo)
user=> (foo 20)
40
```
