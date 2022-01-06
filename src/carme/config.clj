(ns carme.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def config (atom {:host "localhost"
                   :port 1065
                   :basedir "/var/gemini"
                   :index-file "index.gmi"
                   :mime-types {".gmi" "text/gemini"}}))

(defn load-config
  [filename]
  (with-open [reader (java.io.PushbackReader. (io/reader filename))]
    (reset! config (merge @config (edn/read reader)))))

(defn get-config
  [option]
  (get @config option))

(defn get-mime-type
  [suffix]
  (get (get-config :mime-types) suffix))
