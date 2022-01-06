(ns carme.logging)

(def levels {:error 0
             :info  1
             :debug 2})

(def log-level (atom :info))

(def date-format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS"))

(defn set-log-level
  [level]
  (reset! log-level level))

(defn get-log-level
  []
  @log-level)


(defn make-log-message
  [level msg-list]
  (str (->> (java.util.Calendar/getInstance)
            .getTime
            (.format date-format))
       "|"
       (clojure.string/upper-case (name level))
       "|"
       (clojure.string/join " " msg-list)))


(defn will-log?
  [level]
  (<= (get levels level)
      (get levels @log-level)))


(defn log
  [level & msgs]
  (when (will-log? level)
    (println (make-log-message level msgs))))
