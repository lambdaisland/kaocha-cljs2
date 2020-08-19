(ns kaocha.type.cljs2
  (:refer-clojure :exclude [symbol])
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.test :as t]
            [io.pedestal.log :as log]
            [kaocha.cljs2.funnel-client :as funnel]
            [kaocha.hierarchy :as hierarchy]
            [kaocha.output :as output]
            [kaocha.report :as report]
            [kaocha.testable :as testable]
            [kaocha.type :as type]
            [lambdaisland.funnel-client.macros :refer [working-directory]]))

(require 'kaocha.cljs2.print-handlers
         'kaocha.type.var) ;; (defmethod report/fail-summary ::zero-assertions)

(defn client-testable [conn {:keys [id platform platform-type] :as whoami}]
  {::testable/type    ::client
   ::testable/id      (keyword (str "kaocha.cljs2/client:" id))
   ::testable/meta    {}
   ::testable/desc    (str id " (" platform ")")
   ::testable/aliases [(keyword platform-type)]
   :funnel/conn       conn
   :funnel/whoami     whoami})

(defn test-testable [whoami {:keys [name meta]}]
  {::testable/type ::test
   ::testable/id (keyword (str (:id whoami) ":" name))
   ::testable/name name
   ::testable/desc (str name)
   ::testable/meta meta
   ::testable/aliases [(keyword (str name)) (keyword (str (:platform-type whoami) ":" name))]
   ::test name})

(defn ns-testable [whoami {:keys [name meta tests]}]
  {::testable/type ::ns
   ::testable/id (keyword (str (:id whoami) ":" name))
   ::testable/name name
   ::testable/meta meta
   ::testable/desc (str name)
   ::testable/aliases [(keyword name) (keyword (str (:platform-type whoami) ":" name))]
   ::ns name
   :kaocha.test-plan/tests
   (map (partial test-testable whoami) tests)})

(defn resolve-fn [f]
  (cond
    (qualified-symbol? f)
    (requiring-resolve f)

    (list? f)
    (eval f)

    :else
    f))

(defn default-clients-hook [{:funnel/keys [conn]}]
  (funnel/wait-for-clients conn))

(defn type+sender? [msg type whoami]
  (and (= type (:type msg))
       (= (:id whoami)
          (get-in msg [:funnel/whoami :id]))))

(defn test-count [testable]
  (->> testable
       testable/test-seq
       (filter (comp #{::test} ::testable/type))
       count))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod testable/-load :kaocha.type/cljs2 [{:kaocha.cljs2/keys [server-opts
                                                                   clients-hook]
                                               :or                {clients-hook default-clients-hook}
                                               :as                suite}]
  (log/debug :-load/starting suite)
  (let [conn         (funnel/connect)
        suite        (assoc suite
                            :funnel/conn conn
                            ::cwd (working-directory))
        clients-hook (resolve-fn clients-hook)
        client-ids   (clients-hook suite)
        testables    (map (partial client-testable conn) client-ids)
        _ (log/debug :-load/got-clients {:client-ids client-ids})
        tests (testable/load-testables testables)
        _ (log/debug :-load/loaded-tests {:testable-ids (map ::testable/id tests)})]
    (assoc suite
           ::testable/aliases [:cljs]
           ::testable/parallelizable? true
           :kaocha.test-plan/tests tests)))

(defmethod testable/-run :kaocha.type/cljs2 [testable test-plan]
  (t/do-report {:type :begin-test-suite})
  (let [results (testable/run-testables (:kaocha.test-plan/tests testable) test-plan)]
    (t/do-report {:type :end-test-suite})
    (assoc testable :kaocha.result/tests results)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn send-to [{:funnel/keys [conn whoami]} msg]
  (funnel/send conn (assoc msg :funnel/broadcast [:id (:id whoami)])))

(defn listen-to
  ([client handler]
   (listen-to client handler nil))
  ([client handler opts]
   (funnel/listen (:funnel/conn client) handler opts)))

(defn wait-for [client type]
  (funnel/wait-for
   (:funnel/conn client)
   (fn [msg]
     (type+sender? msg type (:funnel/whoami client)))))

(defmethod testable/-load ::client [{:funnel/keys [whoami] :as client}]
  (send-to client {:type             :fetch-test-data
                   :funnel/subscribe [:id (:id whoami)]})
  (listen-to client
             (fn [msg testable]
               (if (type+sender? msg :test-data whoami)
                 (reduced
                  (assoc testable :kaocha.test-plan/tests
                         (map (partial ns-testable whoami) (:test-data msg))))
                 testable))
             {:init client
              :on-timeout
              (fn [testable]
                (throw (ex-info "Timeout while fetching test data"
                                {::fetch-test-data :timeout
                                 :client
                                 (select-keys testable
                                              [:kaocha.testable/id
                                               :kaocha.testable/desc])})))}))

(defmethod testable/-run ::client [{:funnel/keys [conn whoami] :as client} test-plan]
  (t/do-report {:type :kaocha/begin-group})
  (log/debug :run-client/starting {:testable-id (::testable/id client)})

  (send-to client {:type :start-run :test-count (test-count client)})
  (wait-for client :run-started)

  (let [ns-tests (for [ns-test (:kaocha.test-plan/tests client)]
                   (assoc ns-test ::client {:funnel/conn conn :funnel/whoami whoami}))
        ns-tests (testable/run-testables ns-tests test-plan)]

    (send-to client {:type :finish-run})
    (wait-for client :run-finished)

    (t/do-report {:type :kaocha/end-group})
    (assoc client :kaocha.result/tests ns-tests)))

(defmethod testable/-run ::ns [{::keys [ns client]
                                :as testable}
                               test-plan]
  (t/do-report {:type :begin-test-ns})
  (log/debug :run-ns/starting {:testable-id (::testable/id testable)})

  (send-to client {:type :start-ns :ns ns})
  (wait-for client :ns-started)

  (let [var-tests  (for [var-test (:kaocha.test-plan/tests testable)]
                     (assoc var-test ::client client))
        var-tests  (testable/run-testables var-tests test-plan)]

    (send-to client {:type :finish-ns})
    (wait-for client :ns-finished)

    (t/do-report {:type :end-test-ns})
    (assoc testable :kaocha.result/tests var-tests)))

(defmethod testable/-run ::test [{::keys [client test] :as testable} test-plan]
  (t/do-report {:type :begin-test-var})
  (log/debug :run-test/starting {:testable-id (::testable/id testable)})
  (send-to client {:type :run-test :test test})
  (let [testable (listen-to client
                            (fn [msg ctx]
                              (case (:type msg)
                                :cljs.test/message
                                (do
                                  (t/do-report (:cljs.test/message msg))
                                  testable)

                                :test-finished
                                (let [{:keys [summary]} msg]
                                  (reduced
                                   (assoc testable
                                          :kaocha.result/count 1
                                          :kaocha.result/test 1
                                          :kaocha.result/pass (:pass summary 0)
                                          :kaocha.result/fail (:fail summary 0)
                                          :kaocha.result/error (:error summary 0)
                                          :kaocha.result/pending 0)))))
                            {:init testable})]
    (t/do-report {:type :end-test-var})
    testable))

(hierarchy/derive! :kaocha.type/cljs2 :kaocha.testable.type/suite)
(hierarchy/derive! ::client :kaocha.testable.type/group)
(hierarchy/derive! ::ns :kaocha.testable.type/group)
(hierarchy/derive! ::test :kaocha.testable.type/leaf)
(hierarchy/derive! ::timeout :kaocha/fail-type)

(s/def :kaocha.type/cljs2 any? #_(s/keys :req [:kaocha/source-paths
                                               :kaocha/test-paths
                                               :kaocha/ns-patterns]
                                         :opt [:cljs/compiler-options]))

(s/def ::client any?)
(s/def ::ns any?)
(s/def ::test any?)

(defmethod report/dots* ::timeout [m]
  (t/with-test-out
    (print (output/colored :red "T"))
    (flush)) )

(comment
  (require 'kaocha.repl)


  (kaocha.repl/run :lambdaisland.chui-demo.a-test/aa-test)

  (kaocha.repl/run [:firefox-2 :foo.bar/baz])

  (repl/run :lambdaisland.chui-demo.a-test/aa-test)

  :kaocha.testable/id
  :c862dae5-5e27-42ee-a1eb-f75d3b791d56:lambdaisland.chui-demo.a-test/aa-test,

  :kaocha.testable/aliases [:lambdaisland.chui-demo.a-test/aa-test
                            :aa-test]

  :unit
  :editor

  (kaocha.repl/config)
  (map ::testable/aliases (kaocha.testable/test-seq (kaocha.repl/test-plan)))


  )

(comment
  (require 'kaocha.repl)

  (kaocha.repl/run :cljs {:kaocha/tests [{:kaocha.testable/type :kaocha.type/cljs2
                                          }]
                          :kaocha.plugin.capture-output/capture-output? false
                          :kaocha/reporter ['kaocha.report/documentation]})

  (require 'kaocha.type.var)

  )
