(ns carme.logging)

(def levels
  "Available levels for logging"
  {:error 0
   :info  1
   :debug 2})

(def ^:private log-level (atom :info))

(def ^:private date-format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS"))


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
  (str (->> (java.util.Calendar/getInstance)
            .getTime
            (.format date-format))
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
