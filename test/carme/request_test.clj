(ns carme.request-test
  (:require [clojure.test :refer :all]
            [carme.request :refer :all]
            [carme.config :as config])
  (:import [java.net URI]))

(defn set-test-config
  []
  (swap! config/config assoc :host "test-host")
  (swap! config/config assoc :port 1234)
  (swap! config/config assoc :basedir "resources/gemfiles"))


(deftest test-valid-scheme?
  (testing "Test that a URI is using a scheme we recognise"
    (is (valid-scheme? (URI. "gemini://localhost:1965/foo/bar.gmi")))
    (is (not (valid-scheme? (URI. "http://localhost:1965/foo/bar.gmi"))))))

(deftest test-starts-with-feff?
  (testing "Test that a string (representing a URI) doesn't start with invalid 0xff 0xfe characters"
    (let [bad-str  (clojure.string/join [(char 0xff) (char 0xfe) "this is bad"])
          good-str (clojure.string/join [(char 0xfe) (char 0xfe) "this is good"])]
      (is (starts-with-feff? bad-str))
      (is (not (starts-with-feff? good-str))))))

(deftest test-host-and-port-valid?
  (testing "Check that the hostname and port numbers are OK"

    (set-test-config)

    (is (host-and-port-valid? (URI. "gemini://test-host:1234/foo/bar.gmi")))
    (is (not (host-and-port-valid? (URI. "gemini://sausage:1234/foo/bar.gmi"))))
    (is (not (host-and-port-valid? (URI. "gemini://test-host:9999/foo/bar.gmi"))))))

(deftest test-get-normalized-path
  (testing "Make sure the path in the URI is sensible and returns a Path object"
    (let [uri-1 (URI. "gemini://test-host:1965/foo/bar.gmi")
          uri-2 (URI. "gemini://test-host:1965/baz.gmi")
          uri-3 (URI. "gemini://test-host:1965")
          uri-4 (URI. "gemini://test-host:1965/qux.gmi/")]

      (set-test-config)
      (swap! config/config assoc :basedir "/var/gemini")

     (is (= "/var/gemini/foo/bar.gmi" (.toString (get-normalized-path uri-1))))
     (is (= "/var/gemini/baz.gmi"     (.toString (get-normalized-path uri-2))))
     (is (= "/var/gemini"             (.toString (get-normalized-path uri-3))))
     (is (= "/var/gemini/qux.gmi"     (.toString (get-normalized-path uri-4)))))))
