(ns kaocha.cljs2.funnel-client
  (:refer-clojure :exclude [send])
  (:require [clojure.core.async :as async]
            [cognitect.transit :as transit]
            [io.pedestal.log :as log]
            [clojure.java.io :as io]
            [lambdaisland.funnel-client :as funnel])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.net URI)
           (org.java_websocket.client WebSocketClient)
           (org.java_websocket.exceptions WebsocketNotConnectedException)))

(set! *warn-on-reflection* true)

(defn connect ^WebSocketClient []
  (let [chan (async/chan)
        conn (funnel/connect {:on-close
                              (fn [_ {:keys [code reason remote?]}]
                                (async/>!! chan {:type :ws/closed :code code :reason reason :remote? remote?}))
                              :on-message
                              (fn [_ msg]
                                (async/>!! chan msg))
                              :on-error
                              (fn [_ ex]
                                (async/>!! chan {:type :error :exception ex}))
                              :chan chan})]
    conn))

(defn send [^WebSocketClient ws-client msg]
  (funnel/send ws-client msg))

(defn close [^WebSocketClient conn]
  (funnel/disconnect conn))

(defn listen
  "\"Coffee-grinder\" that processes messages we receive from funnel. Messages are
  dispatched to a handler function, a context map
  is threaded through each handler.

  Options: `:timeout` in milliseconds, will call the `:on-timeout` callback when
  reached, defaults to throwing an exception. Set to falsey to wait indefintely.
  `:init`, init value for the context to be threaded through. `:result` gets
  called on each iteration to see if the loop should finish. Defaults to waiting
  for a `reduced` value.
  "
  ([conn handler]
   (listen conn handler nil))
  ([conn
    handler
    {:keys [init timeout on-timeout result]
     :or   {init       {}
            timeout    15000
            on-timeout #(throw (ex-info "Timeout" {::ctx %}))
            result     #(and (reduced? %) @%)}}]
   (let [chan (:chan (meta conn))
         poll (if timeout
                #(async/alt!!
                   chan ([msg _] msg)
                   (async/timeout timeout) ([_ _] :timeout))
                #(async/<!! chan))]
     (loop [message (poll)
            ctx     init]
       (log/trace :message message :ctx ctx)

       (if (= :timeout message)
         (on-timeout ctx)

         (let [ctx (handler message ctx)]
           (if-let [result (result ctx)]
             result
             (recur (poll) ctx))))))))

(defn wait-for [conn pred]
  (listen conn (fn [msg ctx]
                 (if (pred msg)
                   (reduced true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; higher level API

(def client-selector
  {:lambdaisland.chui.remote? true
   :working-directory (.getAbsolutePath (io/file ""))})

(defn list-clients [conn]
  (send conn {:funnel/query client-selector})
  (listen conn
          (fn [msg _]
            (when-let [clients (:funnel/clients msg)]
              (reduced clients)))))

(defn wait-for-clients [conn]
  (send conn {:funnel/query client-selector
              :funnel/subscribe client-selector})
  (let [clients (listen conn
                        (fn [msg _]
                          (if-let [clients (seq (:funnel/clients msg))]
                            (reduced clients)
                            (if-let [whoami (:funnel/whoami msg)]
                              (reduced [whoami]))))
                        {:timeout false})]
    (send conn {:funnel/unsubscribe client-selector})
    clients))
