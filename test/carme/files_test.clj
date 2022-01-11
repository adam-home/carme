(ns carme.files-test
  (:require [clojure.test :refer :all]
            [carme.files :refer :all]
            [carme.config :as config]
            [carme.request :as request])
  (:import (java.net URI)))


(defn set-test-config
  []
  (swap! config/config assoc :host "test-host")
  (swap! config/config assoc :port 1234)
  (swap! config/config assoc :basedir "resources/gemfiles"))


(deftest test-has-index-file
  (testing "See if there is an index file when a Path points to a directory."

    (set-test-config)

    (let [file (request/get-normalized-file (URI. "gemini://my-host:1234"))]
      (is (= true (is-directory? file)))
      (is (= true (has-index-file? file))))))


(deftest test-has-index-file?
  (testing "If the URI points to a directory, check there is an index file there"

    (set-test-config)

    (let [file (request/get-normalized-file (URI. "gemini://my-host:1234"))]
      (is (= true (has-index-file? file))))))


(deftest test-load-index-file
  (testing "If the URI points to a directory that contains an index file, load it"

    (set-test-config)

    (let [file (get-file-or-index (request/get-normalized-file (URI. "gemini://my-host:1234")))]
      (try
        (load-local-file file)
        (catch Exception e
          (println e)
          (is (= false (.getMessage e))))))))
