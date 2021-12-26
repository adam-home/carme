(ns carme.core
  (:require [carme.config :as config]
            [carme.response :as response])
  (:import (java.io FileInputStream BufferedInputStream BufferedOutputStream File)
           (java.security KeyStore)
           (java.nio.file Path)
           (java.net URI InetAddress)
           (javax.net ServerSocketFactory)
           (javax.net.ssl SSLServerSocketFactory KeyManagerFactory SSLContext))
  (:gen-class))

;; (def cfg-hostname "localhost")
;; (def cfg-port     1965)
;; (def cfg-basedir  "./resources/gemfiles")

;; (def basedir-path (-> cfg-basedir File. .toPath))

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


(defn ends-with-crlf?
  [s]
  (clojure.string/ends-with? s "\r\n"))
          

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


(defn is-valid-file?
  [path]
  (println "is-valid-file?" path)
  (let [file (.toFile path)]
    (when-not (.exists file)
      (throw (ex-info "File not found" {:status 51 :extra (.getAbsolutePath file)})))
    (when-not (.canRead file)
      (throw (ex-info "File not readable" {:status 51 :extra (.getAbsolutePath file)})))
    (when-not (.isFile file)
     (throw (ex-info "Not a regular file" {:status 51 :extra (.getAbsolutePath file)})))))


(defn load-local-file
  [local-path]
  (println "Load file" local-path)
  (let [path (.resolve (-> (config/get-config :basedir) File. .toPath) local-path)]
    (println "Resolves to" path)
    (is-valid-file? path)
    (slurp (.toFile path))))


(defn accept-client
  "Accept and process a client connection."
  [client]
  (println "Accepted client" client)
  (let [in  (BufferedInputStream. (.getInputStream client))
        out (BufferedOutputStream. (.getOutputStream client))]
    (try
      (let [uri (read-uri in)]
        (println "Request for uri" uri)
        (let [payload (load-local-file (subs (.getPath uri) 1))] ;; subs to remove leading /
          (response/send-response client in out
                                  20
                                  "text/gemini"
                                  payload)))

      (catch Exception e
        (let [message (.getMessage e)
              {:keys [status extra]} (ex-data e)]
          (println e)
          (response/send-error client in out status message extra))))))
         

(defn get-ssl-context
  [keystore-name password]
  (let [password-char (.toCharArray password)
        keystore (KeyStore/getInstance "JKS")]
     (.load keystore (FileInputStream. keystore-name) password-char)
     (let [kmf (KeyManagerFactory/getInstance "SunX509")]
       (.init kmf keystore password-char)
       (let [ssl-context (SSLContext/getInstance "TLS")]
         (.init ssl-context (.getKeyManagers kmf) nil nil)
         ssl-context))))
        

(defn create-server
  [& {:keys [host port]}]
  (let [ssl-context (get-ssl-context "keystore.jks" "password")
        factory     (.getServerSocketFactory ssl-context)
        socket      (.createServerSocket factory port -1 (InetAddress/getByName host))]
    (println (str "Ready on " host ":" port))
    (loop [client (.accept socket)]
      (-> (Thread. (fn [] (accept-client client)))
          .start)
      (recur (.accept socket)))))
  

(defn -main
  []
  (config/load-config "resources/config.edn")
  (let [server (create-server :host (config/get-config :host)
                              :port (config/get-config :port))]))
