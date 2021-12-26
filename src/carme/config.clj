(ns carme.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def config (atom {}))

(defn load-config
  [filename]
  (with-open [reader (java.io.PushbackReader. (io/reader filename))]
    (reset! config (edn/read reader))))

(defn get-config
  [option]
  (get @config option))

(defn get-mime-type
  [suffix]
  (get (get-config :mime-types) suffix))
