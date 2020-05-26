(ns my.hooks
  (:require [clojure.java.browse :as browse]
            [io.pedestal.log :as log]
            [kaocha.cljs2.funnel-client :as funnel]
            [shadow.cljs.devtools.api :as shadow-api]
            [shadow.cljs.devtools.server :as shadow-server]
            [clojure.core.async :as async]))

(defn launch-browser [{:funnel/keys [conn] :as suite}]
  (when (empty? (funnel/list-clients conn))
    (browse/browse-url "http://localhost:8000"))
  suite)

(defn compile-shadow [suite]
  (log/info :compile-shadow (select-keys suite [:kaocha.suite/id :shadow/build-id]))
  (shadow-server/start!)
  (let [build-state (shadow-api/compile! (:shadow/build-id suite) {})
        build-config (:shadow.build/config build-state)]
    suite))

(defn before-hook [suite]
  (-> suite
      compile-shadow
      launch-browser))
