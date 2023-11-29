(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'io.github.beoliver/wiretap)
(def version (format "0.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def short-hash (b/git-process {:git-args "rev-parse --short --verify master"}))

(defn clean []
  (b/delete {:path "target"}))

(defn jar []
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :scm {:connection "scm:git:https://github.com/beoliver/wiretap.git"
                      :developerConnection "scm:git:ssh://git@github.com/beoliver/wiretap.git"
                      :tag short-hash
                      :url "https://github.com/beoliver/wiretap"}
                :pom-data [[:description "A Clojure library for adding generic trace support without having to modify code."]
                           [:licenses
                            [:license
                             [:name "Eclipse Public License 2.0"]
                             [:url "https://www.eclipse.org/legal/epl-2.0/"]]]]})
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn build [& args]
  (clean)
  (jar))