(ns build
  (:require [clojure.tools.build.api :as b]))

(def basis (delay (b/create-basis {:project "deps.edn"})))
(def class-dir "target/classes")
(def uber-file "target/abductcli.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
  (b/compile-clj {:basis     @basis
                   :src-dirs  ["src"]
                   :class-dir class-dir
                   :ns-compile '[abductcli.core
                                 abductcli.import
                                 abductcli.engine
                                 abductcli.io
                                 abductcli.context
                                 abductcli.anomaly
                                 abductcli.signal
                                 abductcli.memo
                                 abductcli.export]})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     @basis
           :main      'abductcli.core}))
