(ns shadow-cljs-hook
  "Example hook for use with shadow-cljs."
  (:require [clojure.java.browse :as browse]
            [io.pedestal.log :as log]
            [kaocha.cljs2.funnel-client :as funnel]
            [shadow.cljs.devtools.api :as shadow-api]
            [shadow.cljs.devtools.server :as shadow-server]))

(comment

  ;; Example config
  ;;#kaocha/v1
  {:plugins [:kaocha.plugin/hooks]
   :tests [{:type                :kaocha.type/cljs2
            :shadow/build-id     :test
            :browse-url          "http://localhost:8000"
            :kaocha.hooks/before [shadow-cljs-hook/before-hook]}]}

  )

(defn launch-browser
  "Checks if any chui-remote clients for our project are connected. If not it
  opens a new browser window/tab. Uses the `:browse-url` configured in the test
  suite, defaults to localhost:8000."
  [{:funnel/keys [conn] :as suite}]
  (when (empty? (funnel/list-clients conn))
    (browse/browse-url (:browse-url suite "http://localhost:8000")))
  suite)

(defn compile-shadow
  "Start shadow's server and compiles the build specified with `:shadow/build-id`
  in the test suite configuration. Defaults to looking for a build called `:test`."
  [suite]
  (log/info :compile-shadow (select-keys suite [:kaocha.testable/id :shadow/build-id]))
  (shadow-server/start!)
  (let [build-state (shadow-api/compile! (:shadow/build-id suite :test) {})
        build-config (:shadow.build/config build-state)]
    suite))

(defn before-hook [suite _]
  (-> suite
      compile-shadow
      launch-browser))
