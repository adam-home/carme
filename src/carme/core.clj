(ns carme.core
  (:require [clojure.java.io :as io]
            [carme.config :as config]
            [carme.request :as request]
            [carme.response :as response]
            [carme.logging :as logging])
  (:import (java.io FileInputStream BufferedInputStream BufferedOutputStream File)
           (java.security KeyStore)
           (java.nio.file Path Files)
           (java.net URI InetAddress)
           (javax.net ServerSocketFactory)
           (javax.net.ssl SSLServerSocketFactory KeyManagerFactory SSLContext))
  (:gen-class))


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
  (logging/log :info "Accepted client :" client)

  (let [in  (BufferedInputStream. (.getInputStream client))
        out (BufferedOutputStream. (.getOutputStream client))]
    (try
      (let [uri (request/read-uri in)]
        (logging/log :debug "Request for uri" uri)

        (let [path (get-file-or-index (request/get-normalized-path uri))]
          (if-let [result (load-local-file path)]
            (response/send-response client in out
                                    20
                                    (:mime-type result)
                                    (:content result))
            (throw (ex-info "Unable to find file to serve" {:status 59 :extra (.toString path)})))))
      (catch Exception e
        (let [message (.getMessage e)
              {:keys [status extra]} (ex-data e)]
          (logging/log :error e)
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

    (logging/log :info (str "Ready on " host ":" port))

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
