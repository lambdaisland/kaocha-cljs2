(ns kaocha.cljs2.node
  (:require [cljs.build.api :as cljs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [io.pedestal.log :as log]
            [kaocha.testable :as testable])
  (:import (com.google.common.io Files)
           (java.io InputStream InputStreamReader)))

(set! *warn-on-reflection* true)

(defn build [source output-dir]
  (cljs/build source
              (merge {:infer-externs true
                      :install-deps true
                      :target :nodejs
                      :optimizations :simple
                      :output-to (str (io/file output-dir "test.js"))
                      :source-map (str (io/file output-dir "test.js.map"))
                      :output-dir (str output-dir)}
                     (:kaocha.cljs2/compile-opts testable/*current-testable*))))

(defn copy-to-fn [^InputStream input f]
  (let [^"[C" buffer (make-array Character/TYPE 1024)
        in (InputStreamReader. input "UTF-8")]
    (loop []
      (let [size (.read in buffer 0 (alength buffer))]
        (if (pos? size)
          (let [s (String/copyValueOf buffer 0 size)]
            (doseq [line (str/split s #"\R")]
              (f line))
            (recur)))))))

(defn invoke-node ^Process [& args]
  (let [pb (ProcessBuilder. ^java.util.List (cons "node" args))
        _ (doto (.environment pb) ;; find node_modules in JVM CWD, where :install-deps will drop them
            (.put "NODE_PATH" (str (io/file (System/getProperty "user.dir") "node_modules"))))
        process (.start pb)]
    (future
      (with-open [stdout (.getOutputStream process)]
        (copy-to-fn stdout #(log/debug :node-stdout %))))
    (future
      (with-open [stderr (.getErrorStream process)]
        (copy-to-fn stderr #(log/error :node-stderr %))))
    process))

(defn pre-load-test [suite config]
  (let [output-dir (Files/createTempDir)]
    (build '#{kaocha.cljs2.autorequire lambdaisland.chui.remote} output-dir)
    (let [node (invoke-node (str (io/file output-dir "test.js")))]
      (update suite :kaocha.hooks/post-test (fnil conj []) (fn [test _] (.destroy node) test)))))
