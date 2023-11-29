(ns deploy
  (:require [deps-deploy.deps-deploy :as deps-deploy]
            [clojure.java.io :as io]))

(defn path-to-jar-in-dir [dir]
  (->> (io/file dir)
       (.listFiles)
       (filter #(re-matches #".*\.jar$" (.getName %)))
       (first)
       (.getPath)))

(defn deploy [& args]
  (let [artifact (path-to-jar-in-dir "target")
        pom-dir "target/classes/META-INF/maven/io.github.beoliver/wiretap"
        pom-file (str pom-dir "/pom.xml")
        pom-properties (str pom-dir "/pom.properties")] 
    (println "Deploying" artifact "with pom" pom-file)
    (println (slurp pom-properties))
    ;; prompt for confirmation using y/N pattern
    (println "Deploy? [y/N] ") 
    (let [input (read-line)]
      (when (#{"y" "Y"} input)
        (deps-deploy/deploy
         {:installer :remote
          :sign-releases? false
          :artifact artifact
          :pom-file pom-file})))))