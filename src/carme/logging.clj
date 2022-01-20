(ns carme.logging)

(def levels
  "Available levels for logging"
  {:error 0
   :info  1
   :debug 2})

(def ^:private log-level (atom :info))

(def ^:private date-format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS"))

(defn- get-date-time-now
  []
  (->> (java.util.Calendar/getInstance)
       .getTime
       (.format date-format)))


(defn set-log-level
  "Set the current logging level"
  [level]
  (reset! log-level level))


(defn get-log-level
  "Get the current logging level."
  []
  @log-level)


(defn make-log-message
  "Format a message to be output."
  [level msg-list]
  (str (get-date-time-now)
       "|"
       (clojure.string/upper-case (name level))
       "|"
       (clojure.string/join " " msg-list)))


(defn will-log?
  "Returns true if the supplied logging level will result in output
  being logged."
  [level]
  (<= (get levels level)
      (get levels @log-level)))


(defn log
  "Attempt to output a logging message at the specified log level."
  [level & msgs]
  (when (will-log? level)
    (println (make-log-message level msgs))))


(defn- get-peer-principal
  [session]
  (try
    (.getPeerPrincipal session)
    (catch javax.net.ssl.SSLPeerUnverifiedException ex
      "anon")))


(defn log-access
  [client resource]
  (let [session (.getSession client)]
    (log :info
         "ACCESS"
         (.getPeerHost session)
         (get-peer-principal session)
         resource)))


