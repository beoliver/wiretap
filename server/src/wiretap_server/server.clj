(ns wiretap-server.server
  (:require [wiretap-server.logging :as log]
            [org.httpkit.server :as server]
            [ruuter.core :as ruuter]
            [cheshire.core :as json]
            [wiretap-server.commands :as commands]
            [clojure.java.io :as io]))

;; https://github.com/askonomm/ruuter?tab=readme-ov-file

(def empty-state {:socket nil :server nil :websocket-ch nil})

(defonce state (atom empty-state))

(defn init-event-socket! [port callback-fn]
  (log/info {:init-event-socket! callback-fn})
  (when (:socket @state)
    (.close (:socket @state)))
  (let [server-socket (new java.net.ServerSocket port)]
    (.setReuseAddress server-socket true)
    (future
      (try
        (while true
          (let [socket (.accept server-socket)]
            (log/info "accepted socket connection from clojure project")
            (future
              (try (let [input-stream (.getInputStream socket)
                         reader (new java.io.BufferedReader (new java.io.InputStreamReader input-stream))]
                     (loop [msg (.readLine reader)]
                       (when msg
                         (let [edn (load-string msg)]
                           (log/info {:message edn})
                           (callback-fn edn))
                         (recur (.readLine reader)))))
                   (catch Exception e (log/error e))
                   (finally (.close socket))))))
        (catch Exception e (log/error e))))
    (swap! state assoc :socket server-socket)))

(comment
  (init-event-socket! 9876
                      (fn [edn]
                        (if-some [websocket-ch (:websocket-ch @state)]
                          (server/send! websocket-ch (json/encode edn))
                          (log/info {:init-event-socket! {:no-ws true
                                                          :edn edn}}))))
  (commands/load-wiretap! 56442)
  (commands/uninstall-wiretaps! 56442)
  )

(defn ws-connect-handler [req]
  (log/info {:ws-connect-handler true}) 
  (server/as-channel
   req
   {:on-open (fn [ch]
               (when (:websocket-ch @state)
                 (log/info "closing existing ws channel")
                 (server/close (:websocket-ch state)))
               (swap! state assoc :websocket-ch ch))
    :on-close (fn [ch status-code]
                (log/info {:status-code status-code
                           :what "channel closed"})) 
    }))

(defn nrepl-connect-handler [{:keys [params] :as req}] 
  (log/info {:nrepl-connect-handler true}) 
  (if-not (:websocket-ch @state)
    {:status 500}
    (let [nrepl-port (Long/parseLong (:nrepl-port params))]
      (log/info {:nrepl-port nrepl-port})
      (init-event-socket! 9876
                          (fn [edn]
                            (if-some [websocket-ch (:websocket-ch @state)] 
                              (do 
                                (log/info {:server-send edn})
                                (server/send! websocket-ch (json/encode edn)))
                              (log/info edn))))
      (commands/load-wiretap! nrepl-port))))


(def routes [{:path "/test"
              :method :get
              :response (fn [_]
                          {:status 200
                           :body (slurp (io/resource "public/test.html"))})}
             {:path "/"
              :method :get
              :response (fn [_]
                          {:status 200
                           :body (slurp (io/resource "public/index.html"))})}
             {:path "/app.js"
              :method :get
              :response (fn [_]
                          {:status 200
                           :headers {"Content-Type" "text/javascript"}
                           :body (slurp (io/resource "public/app.js"))})}
             {:path "/style.css"
              :method :get
              :response (fn [_]
                          {:status 200
                           :body (slurp (io/resource "public/style.css"))})}
             {:path "/favicon.ico"
              :method :get
              :response (fn [_]
                          {:status 200
                           :body (slurp (io/resource "public/favicon.ico"))})}
             {:path "/connect-ws"
              :method :get
              :response ws-connect-handler}
             {:path "/connect-nrepl/:nrepl-port"
              :method :get
              :response nrepl-connect-handler}
             {:path "/wiretapped/:id/arg/:index"
              :method :get
              :response {:status 200
                         :body "ARG index"}}
             {:path "/wiretapped/:id/result"
              :method :get
              :response {:status 200
                         :body "Result"}}
             ])


(defn app [req]
  (ruuter/route routes req))

(defn start-server!
  "Starts the server on the given port."
  [port]
  (when-some [server (:server @state)]
    (server))
  (when-some [client-websocket (:websocket-ch @state)]
    (try (server/close client-websocket)
         (catch Exception e (log/error e))))
  (when-some [socket (:socket @state)]
    (try (.close socket)
         (catch Exception e (log/error e))))
  (let [server (server/run-server #'app {:port port})]
    (log/info (str "Server started on port " port)) 
    (reset! state (assoc empty-state :server server))))

(comment
  (start-server! 7777)
  )
