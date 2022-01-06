(ns carme.core
  (:require [clojure.java.io :as io]
            [carme.config :as config]
            [carme.response :as response])
  (:import (java.io FileInputStream BufferedInputStream BufferedOutputStream File)
           (java.security KeyStore)
           (java.nio.file Path Files)
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


(defn is-valid-file?
  [path]
  (let [file (.toFile path)]
    (and (.exists file)
         (.canRead file)
         (.isFile file))))

(defn is-directory?
  [path]
  (let [file (.toFile path)]
    (and (.exists file)
         (.isDirectory file))))

(defn has-index-file?
  [dir-path]
  (and (is-directory? dir-path)
       (is-valid-file? (.resolve dir-path (config/get-config :index-file)))))


(defn get-file-or-index
  "Takes a Path, and if it points to a file, return the Path unchanged.
  If the Path points to a directory, see if there is an index file in the directory, and return a Path to that.
  Otherwise, return false."
  [path]
  (if (has-index-file? path)
    (.resolve path (config/get-config :index-file))
    (if (is-valid-file? path)
      path
      false)))

(defn guess-mime-type
  "Guess the mime type of a file, specificed by a Path.

  If the filename extension is specified in the user's configuration
  file (e.g. \".gmi\"), use that. If not present, try to guess using
  Files/probeContentType. Finally, assume application/octet-stream."
  [path]
  (let [user-type (config/get-mime-type
                    (str "." (last (clojure.string/split (.toString path) #"\."))))
        sys-type  (Files/probeContentType path)]

    (or user-type
        sys-type
        "application/octet-stream")))


(defn file->bytes
  "Given a File, read the contents and return as a byte array."
  [file]
  (with-open [in (io/input-stream file)
              out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))


(defn load-local-file
  "For a Path, check for a file at the configured basedir + Path.

  If it's found, return a map containing the mime-type (string) and content (bytes).
  If it's not found, an Exception is thrown."
  [path]
  (when-not (is-valid-file? path)
    (throw (ex-info "Invalid file" {:status 59 :extra path})))
  {:mime-type (guess-mime-type path)
   :content   (file->bytes (.toFile path))})


(defn accept-client
  "Accept and process a client connection."
  [client]
  (println "Accepted client  :" client)

  ;; (let [session (.getSession client)]
  ;;   (println "Protocol :" (.getProtocol session))
  ;;   (println "Peer host:" (.getPeerHost session))
  ;;   (println "Peer port:" (.getPeerPort session))

  ;;   (println "Peer principal:"
  ;;            (try
  ;;              (.getPeerPrincipal session)
  ;;              (catch Exception e
  ;;                (println "!!!! Exception:" (.getMessage e))
  ;;                "Unable to get principal")))

  ;;   (println "Peer certificates:"
  ;;        (try
  ;;          (.getPeerCertificates session)
  ;;          (catch Exception e
  ;;            (println "!!!! Exception:" (.getMessage e))
  ;;            "No certs"))))  

  (let [in  (BufferedInputStream. (.getInputStream client))
        out (BufferedOutputStream. (.getOutputStream client))]
    (try
      (let [uri (read-uri in)]
        (println "Request for uri" uri)

        (let [path (get-file-or-index (get-normalized-path uri))]
          (if-let [result (load-local-file path)]
            (response/send-response client in out
                                    20
                                    (:mime-type result)
                                    (:content result))
            (throw (ex-info "Unable to find file to serve" {:status 59 :extra (.toString path)})))))
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


(defn handle-client
  [client]
  (-> (Thread. (fn [] (accept-client client)))
      .start))


(defn create-server
  [& {:keys [host port]}]
  (let [ssl-context    (get-ssl-context "keystore.jks" "password")
        socket-factory (.getServerSocketFactory ssl-context)
        socket         (.createServerSocket socket-factory port -1 (InetAddress/getByName host))]

    (println (str "Ready on " host ":" port))

    (-> (Thread. (fn []
                   (loop [client (.accept socket)]
                     (handle-client client)
                     (recur (.accept socket)))))
        .start)))


(defn -main
  []
  (config/load-config "resources/config.edn")
  (let [server (create-server :host (config/get-config :host)
                              :port (config/get-config :port))]))
