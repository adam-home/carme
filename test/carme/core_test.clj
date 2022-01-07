(ns carme.core-test
  (:require [clojure.test :refer :all]
            [carme.core :refer :all]
            [carme.config :as config]
            [carme.request :as request])
  (:import (java.net URI)
           (java.nio.file Path)))

(defn set-test-config
  []
  (swap! config/config assoc :host "test-host")
  (swap! config/config assoc :port 1234)
  (swap! config/config assoc :basedir "resources/gemfiles"))


(deftest test-has-index-file
  (testing "See if there is an index file when a Path points to a directory."

    (set-test-config)
    
    (let [path (request/get-normalized-path (URI. "gemini://my-host:1234"))]
      (is (= true (is-directory? path)))
      (is (= true (has-index-file? path))))))
          

(deftest test-has-index-file?
  (testing "If the URI points to a directory, check there is an index file there"

    (set-test-config)

    (let [path (request/get-normalized-path (URI. "gemini://my-host:1234"))]
      (is (= true (has-index-file? path))))))


(deftest test-load-index-file
  (testing "If the URI points to a directory that contains an index file, load it"

    (set-test-config)

    (let [path (get-file-or-index (request/get-normalized-path (URI. "gemini://my-host:1234")))]
      (try
        (load-local-file path)
        (catch Exception e
          (is (= false (.getMessage e))))))))

