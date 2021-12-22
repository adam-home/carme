(ns carme.response)

(def status-codes {
                   20 "SUCCESS"
                   50 "PERMANENT FAILURE"
                   51 "NOT FOUND"
                   52 "GONE"
                   53 "PROXY REQUEST REFUSED"
                   59 "BAD REQUEST"})


(defn- send-data
  [out data]
  (.write out (.getBytes data)))
          

(defn- close
  [client in out]
  (.flush out)
  (.close in)
  (.close out)
  (.close client))

(defn- send-and-close
  "Send a response to a client, and immediately close the connection."
  ([client in out meta]
   (send-data out meta)
   (close client in out))

  ([client in out meta payload]
   (send-data out meta)
   (send-data out payload)
   (close client in out)))

(defn- get-meta
  "Given a status code and optional mime-type, format and return a
  metadata string for the response."
  ([status]
   (str status " " (get status-codes status "UNKNOWN") "\r\n"))
  ([status mime-type]
   (str status " " mime-type "\r\n")))

(defn send-response
  "Send a response to a client, given a mime type and payload. Closes
  the connection once done."
  [client in out status mime-type payload]
  (let [meta (get-meta status mime-type)]
    (send-and-close client in out meta payload)))

(defn send-error
  "Send an error response to the client, and close the connection."
  [client in out status message extra-data]

  (let [meta (get-meta status)]

      (println "RESPONSE ----------")
      (println meta)
      (println "MESSAGE:" message)
      (println "EXTRA  :" extra-data)

      (send-and-close client in out meta)))
  
