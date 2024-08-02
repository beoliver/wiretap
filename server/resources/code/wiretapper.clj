(ns wiretapper
  (:require [wiretap.wiretap :as wt]))

(println "LOADING...")

;; this namespace should not be evaluated in this project - but 
;; rather "injected" into the project that you want to explore.

(def ^:private wiretap-server-port 9876)

;; port is hard coded for now. This port is opened by the babashka server. 
;; the project being wiretapped will then send (small) messages over this socket. 
;; these messages allow us to send back ids and put them on a websocket
;; note that the full wiretap context is not sent over this socket.

(defonce ^:private state (atom {}))

(defonce ^:private socket (atom nil))
(defonce ^:private writer (atom nil))

(defn close-socket! []
  (when @socket (.close @socket)))

(defn reset-state! []
  (reset! state {}))

(defn init! []
  (println "Called INIT in " *ns*)
  (close-socket!)
  (reset-state!)
  (reset! socket (new java.net.Socket "localhost" wiretap-server-port))
  (reset! writer (new java.io.PrintWriter (.getOutputStream @socket) true)))

(defn- send-message! [{:keys [id pre? post? args result] :as wiretap-context}]
  (let [data (cond-> {:id id}
               pre? (assoc :state :pre
                           :arg-types (mapv #(.getSimpleName (type %)) args))
               post? (assoc :state :post
                            :result-type (.getSimpleName (type result))))]
    (locking writer
      (when @writer
        (.println @writer data)))))

(defn- the-wiretap [{:keys [id pre?] :as wiretap-context}]
  (swap! state #(assoc-in % [id (if pre? :pre :post)] wiretap-context))
  (send-message! wiretap-context))

(defn install-wiretap! [vars]
  (wt/install! the-wiretap vars))

;; init the ns strait away

(init!)
