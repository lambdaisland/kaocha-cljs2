(ns kaocha.cljs2.funnel
  (:import (org.java_websocket.client WebSocketClient))
  )

(def funnel-port 44220)

(defn test-client [message-handler]
  (let [history (atom [])
        connected? (promise)
        client (proxy [WebSocketClient] [(URI. (str "ws://localhost:" funnel-port))]
                 (onOpen [handshake]
                   (deliver connected? true))
                 (onClose [code reason remote?])
                 (onMessage [message]
                   (swap! history conj (funnel/from-transit message)))
                 (onError [ex]
                   (println ex)
                   ))]
    (.connect client)
    @connected?
    (map->TestClient
     {:history history
      :client client})))
