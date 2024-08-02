(ns wiretap-server.commands
  (:require [wiretap-server.state :refer [messages]]
            [wiretap-server.nrepl :as nrepl-client]))

(def root-path
  (second (re-matches #"(.*)/server" (System/getProperty "user.dir"))))

(defn load-wiretap! [nrepl-port]
  (let [path-to-wiretap (str root-path "/src/wiretap/wiretap.clj")
        path-to-wiretapper (str root-path "/server/resources/code/wiretapper.clj")
        load-expr (format "(do (load-file \"%s\") (load-file \"%s\"))" path-to-wiretap path-to-wiretapper)
        data {:host "localhost" :port nrepl-port :expr load-expr}
        res (nrepl-client/eval-expr data)] 
    (swap! messages conj {:load-wiretap! {:in data :result res}})
    res))

(defn uninstall-wiretaps! [nrepl-port] 
  (let [expr "(wiretap.wiretap/uninstall!)"]
    (nrepl-client/eval-expr {:host "localhost" :port nrepl-port :expr expr})))

