(ns ^:no-doc kaocha.cljs2.autorequire
  #?(:clj
     (:require [clojure.java.io :as io]
               [kaocha.load :as load]
               [kaocha.testable :as testable]
               ))
  #?@(:cljs
      ((:require [lambdaisland.chui.test-data :as test-data])
       (:require-macros [kaocha.cljs2.autorequire :refer [require-test-namespaces!]]))))

#?(:cljs (require-test-namespaces!))
#?(:cljs (test-data/capture-test-data!))

#?(:clj
   (defn regex
     ([x & xs]
      (regex (apply str x xs)))
     ([x]
      (cond
        (instance? java.util.regex.Pattern x) x
        (string? x) (re-pattern x)
        :else       (throw (ex-info (str "Can't coerce " (class x) " to regex.") {}))))))

#?(:clj
   (defmacro require-test-namespaces! []
     (assert (= :kaocha.type/cljs2 (:kaocha.testable/type testable/*current-testable*)))
     (let [{:kaocha/keys [ns-patterns test-paths]} testable/*current-testable*]
       `(require ~@(map #(list 'quote %) (load/find-test-nss test-paths (map regex ns-patterns) load/cljs))))))
