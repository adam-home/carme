(ns carme.request
  (:require [carme.config :as config])
  (:import [java.io File]
           [java.net URI]
           [java.nio.file Path]))

(defn read-request
  "Read a request line from the input stream, up to \r\n"
  [in]
  (loop [line      []
         last-char nil
         this-byte (.read in)]
    (if (= -1 this-byte)
      (clojure.string/join line)
      (let [this-char (char this-byte)]
        (if (and (= \return last-char)
                 (= \newline this-char))
          (clojure.string/join line)
          (recur (conj line this-char)
               this-char
               (.read in)))))))


(defn starts-with-feff?
  "Checks to see if a string (URL) starts with the invalid \\xffef
  characters."
  [s]
  (if (>= (count s) 2)
    (let [chars (.toCharArray s)]
      (and (= (char 0xff) (first chars))
           (= (char 0xfe) (second chars))))
    false))

(defn valid-scheme?
  "Check to see if the URI's scheme is supported. Only \"gemini\" is
  valid at the moment."
  [uri]
  (= "gemini" (.getScheme uri)))


(defn host-and-port-valid?
  "Check to see if the hostname matches the configured name, as does
  the port number (if supplied)."
  [uri]
  (let [host (.getHost uri)
        port (.getPort uri)]
    (and (= host (config/get-config :host))
         (or (= port -1)
             (= port (config/get-config :port))))))


(defn get-normalized-path
  "Reads a URI and returns a Path object for the path. Removes any
  leading, trailing or duplicated slashes."
  [uri]

  (let [basedir-parts (clojure.string/split (config/get-config :basedir) #"/")
        uri-parts     (clojure.string/split (.getPath uri) #"/")
        path-str      (as-> (concat basedir-parts uri-parts) $     ;; Append URI to basedir
                        (filter (fn [x] (not= "" x)) $)            ;; Remove empty path components
                        (clojure.string/join "/" $))]              ;; Join remaining path components

    ;; Convert to Path. If basedir starts with a /, then the Path should too.
    (Path/of (str
               (if (clojure.string/starts-with? (config/get-config :basedir) "/")
                 "/"
                 "")
               path-str)
             (into-array [""]))))

(defn valid-path?
  "Ensures that the path in a URI is valid, e.g. does not contain \"..\"
  to break out of the specified gemfile directory."
  [uri]
  (let [path (.toPath (File. (.getPath uri)))]
    (not-any? (fn [part] (= ".." (.toString part)))
              (iterator-seq (.iterator path)))))


(defn get-uri-from-string
  "Performs all checks to ensure that the URI is valid. Throws an
  Exception if not."
  [uri-str]
  (when (starts-with-feff? uri-str)
    (throw (ex-info "URI string starts with invalid characters" {:status 59 :extra uri-str})))

  (let [uri (.normalize (URI. (clojure.string/trim uri-str)))]
    (when-not (valid-scheme? uri)
      (throw (ex-info "Invalid URI" {:status 59 :extra uri-str})))
    (when-not (host-and-port-valid? uri)
      (throw (ex-info "Invalid host/port" {:status 53 :extra uri-str})))
    (when-not (valid-path? uri)
      (throw (ex-info "Invalid path" {:status 59 :extra uri-str})))
    uri))


(defn read-uri
  "Read and return a URI from the input. If an error occurs,
  an Exception is thrown."
  [in]
  (let [uri-str (read-request in)
        uri     (get-uri-from-string uri-str)]
    uri))
