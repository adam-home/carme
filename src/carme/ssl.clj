(ns carme.ssl
  (:require [clojure.java.io :as io])
  (:import [java.security Security KeyStore]
           [javax.net.ssl SSLContext KeyManagerFactory]
           [org.bouncycastle.jce.provider BouncyCastleProvider]
           [org.bouncycastle.openssl PEMParser]
           [org.bouncycastle.openssl.jcajce JcaPEMKeyConverter]
           [org.bouncycastle.cert.jcajce JcaX509CertificateConverter]
           [java.io StringReader]))

;; Add BouncyCastle as a security provider
(Security/addProvider (BouncyCastleProvider.))

(defn read-pem-file
  "Read a PEM file and return its contents as a string"
  [file-path]
  (slurp file-path))

(defn parse-certificate
  "Parse a PEM certificate string and return X509Certificate"
  [cert-pem]
  (with-open [reader (StringReader. cert-pem)]
    (let [pem-parser  (PEMParser. reader)
          cert-holder (.readObject pem-parser)
          converter   (JcaX509CertificateConverter.)]
      (.getCertificate converter cert-holder))))

(defn parse-private-key
  "Parse a PEM private key string and return PrivateKey"
  [key-pem]
  (with-open [reader (StringReader. key-pem)]
    (let [pem-parser (PEMParser. reader)
          key-pair (.readObject pem-parser)
          converter (JcaPEMKeyConverter.)]

      (cond
        ;; Handle PKCS#8 private key info
        (instance? org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo key-pair)
        (throw (ex-info "Encrypted private keys not supported in this example" {}))
        
        ;; Handle unencrypted PKCS#8 private key info
        ;; (instance? org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo key-pair)
        ;; (.getPrivateKey converter key-pair)
        
        ;; Handle key pair
        (instance? org.bouncycastle.openssl.PEMKeyPair key-pair)
        (.getPrivateKey converter key-pair)
        
        ;; Handle direct private key
        :else
        (.getPrivateKey converter key-pair)))))

(defn create-keystore
  "Create a KeyStore with the certificate and private key"
  [private-key certificate]
  (let [keystore (KeyStore/getInstance "PKCS12")
        cert-chain (into-array [certificate])
        alias "letsencrypt"]
    (.load keystore nil nil)
    (.setKeyEntry keystore alias private-key nil cert-chain)
    keystore))

(defn create-ssl-context
  "Create an SSLContext using the certificate and private key PEM files"
  [key-file-path cert-file-path]
  (try
    (let [cert-pem    (read-pem-file cert-file-path)
          key-pem     (read-pem-file key-file-path)
          certificate (parse-certificate cert-pem)
          private-key (parse-private-key key-pem)
          keystore (create-keystore private-key certificate)
          
          ;; Create KeyManagerFactory
          kmf (KeyManagerFactory/getInstance "SunX509")
          _ (.init kmf keystore nil)
          
          ;; Create SSLContext
          ssl-context (SSLContext/getInstance "TLS")]
      
      (.init ssl-context (.getKeyManagers kmf) nil nil)
      ssl-context)
    
    (catch Exception e
      (throw (ex-info "Failed to create SSL context" 
                      {:error (.getMessage e)} e)))))
