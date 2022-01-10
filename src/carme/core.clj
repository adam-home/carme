(ns carme.core
  (:require [carme.config :as config]
            [carme.request :as request]
            [carme.response :as response]
            [carme.logging :as logging]
            [carme.files :as files])
  (:import (java.io FileInputStream BufferedInputStream BufferedOutputStream)
           (java.security KeyStore)
           (java.net URI InetAddress)
           (javax.net.ssl KeyManagerFactory SSLContext))
  (:gen-class))


(defn- process-client
  "Process a client connection."
  [client]
  (logging/log :info "Accepted client :" client)

  (let [in  (BufferedInputStream.  (.getInputStream client))
        out (BufferedOutputStream. (.getOutputStream client))]
    (try
      (let [uri (request/read-uri in)]
        (logging/log :debug "Request for uri" uri)

        (let [path (files/get-file-or-index (request/get-normalized-path uri))]
          (if-let [result (files/load-local-file path)]
            (response/send-response client in out
                                    20
                                    (:mime-type result)
                                    (:content result))
            (throw (ex-info "Unable to find file to serve" {:status 59 :extra (.toString path)})))))
      (catch Exception e
        (let [message                (.getMessage e)
              {:keys [status extra]} (ex-data e)]
          (logging/log :error e)
          (response/send-error client in out status message extra))))))


(defn- get-ssl-context
  "Load the SSL context from the keystore with the given filename and
  password."
  [keystore-name password]
  (let [password-char (.toCharArray password)
        keystore      (KeyStore/getInstance "JKS")]
     (.load keystore (FileInputStream. keystore-name) password-char)

     (let [kmf (KeyManagerFactory/getInstance "SunX509")]
       (.init kmf keystore password-char)

       (let [ssl-context (SSLContext/getInstance "TLS")]
         (.init ssl-context (.getKeyManagers kmf) nil nil)
         ssl-context))))


(defn- handle-client
  "Spin off a Thread for processing a client connection."
  [client]
  (-> (Thread. (fn [] (process-client client)))
      .start))


(defn create-server
  "Create and start the Gemini server. Returns the Thread that is running the server."
  [& {:keys [host port]}]
  (let [ssl-context    (get-ssl-context "keystore.jks" "password")
        socket-factory (.getServerSocketFactory ssl-context)
        socket         (.createServerSocket socket-factory port -1 (InetAddress/getByName host))]

    (logging/log :info (str "Ready on " host ":" port))

    (let [server-thread (Thread. (fn []
                                   (loop [client (.accept socket)]
                                     (handle-client client)
                                     (recur (.accept socket)))))]
      (.start server-thread)
      server-thread)))


(defn -main
  []
  (config/load-config "resources/config.edn")
  (let [server (create-server :host (config/get-config :host)
                              :port (config/get-config :port))]))
