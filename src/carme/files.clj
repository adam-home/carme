(ns carme.files
  (:require [carme.config :as config]
            [clojure.java.io :as io])
  (:import (java.nio.file Files)))


(defn is-valid-file?
  [file]
  (and (.exists file)
       (.canRead file)
       (.isFile file)))

(defn is-directory?
  [file]
  (and (.exists file)
       (.isDirectory file)))

(defn has-index-file?
  [file]
  (and (is-directory? file)
       (is-valid-file? (io/file file (config/get-config :index-file)))))

(defn is-gemini-file?
  [file]
  (clojure.string/ends-with? (.toString file) ".gmi"))


(defn get-file-or-index
  "Takes a File, and if it points to a file, return the File unchanged.
  If the File points to a directory, see if there is an index file in
  the directory, and return a File representing that.  Otherwise,
  return false."
  [file]
  (if (has-index-file? file)
    (io/file file (config/get-config :index-file))
    (if (is-valid-file? file)
      file
      false)))


(defn guess-mime-type
  "Guess the mime type of a file, specified by a File.

  If the filename extension is specified in the user's configuration
  file (e.g. \".gmi\"), use that. If not present, try to guess using
  Files/probeContentType. Finally, assume application/octet-stream."
  [file]
  (let [user-type (config/get-mime-type
                    (str "." (last (clojure.string/split (.toString file) #"\."))))
        sys-type  (Files/probeContentType (.toPath file))]

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
  "For a File, check for a file at the configured basedir + File.

  If it's found, return a map containing the mime-type (string) and content (bytes).
  If it's not found, an Exception is thrown."
  [file]
  (when-not (is-valid-file? file)
    (throw (ex-info "Invalid file" {:status 59 :extra file})))
  {:mime-type (guess-mime-type file)
   :content   (file->bytes file)})


(defn get-link-for-file
  "Build a string representation of the URI for accessing this file."
  [file]
  (str "gemini://"
       (config/get-config :host) ":" (config/get-config :port)
       (clojure.string/replace-first (.toString file)
                                     (config/get-config :basedir)
                                     "")))


(def date-time-format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss"))

(defn format-date-time-nicely
  "Format a Date in a nicer to read way."
  [date]
  (.format date-time-format date))

(defn get-file-creation-date
  "Get the creation date of a file as a java.util.Date. object."
  [file]
  (-> (.toPath file)
      (Files/readAttributes java.nio.file.attribute.BasicFileAttributes (into-array java.nio.file.LinkOption []))
      .creationTime
      .toMillis
      java.util.Date.))

(defn try-get-title-from-line
  "Strip leading # chars from line and trim whitespace."
  [line]
  (when (clojure.string/starts-with? line "#")
    (-> line
        (clojure.string/replace #"^(.*?)#+" "")
        clojure.string/trim)))


(defn get-title-from-filename
  "Strip gemini suffix from a filename if present."
  [filename]
  (clojure.string/replace filename #"\.gmi$" ""))

(defn get-gemfile-title
  "Look for the first heading in a gemfile to use as a title. If there
  is no heading, use the filename instead."
  [file]
  (with-open [reader (io/reader file)]
    (let [found-title (loop [title nil
                             lines (line-seq reader)]

                        (if (or title
                                (empty? lines))
                          title
                          (recur (try-get-title-from-line (first lines))
                                 (rest lines))))]
      (or found-title
          (get-title-from-filename (.getName file))))))


(defn generate-index
  "Automatically create a gemtext index of files in the directory of
  the given File."
  [dir]
  (let [files        (->> (file-seq dir)
                          (filter is-gemini-file?)
                          (filter is-valid-file?))
        sorted-files (reverse (sort-by get-file-creation-date files))
        entries      (for [f sorted-files] (str "=> "
                                                (get-link-for-file f)
                                                " "
                                                (format-date-time-nicely (get-file-creation-date f))
                                                " "
                                                (get-gemfile-title f)))]
    (.getBytes (clojure.string/join "\r\n" entries))))
