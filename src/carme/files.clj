(ns carme.files
  (:require [carme.config :as config]
            [clojure.java.io :as io])
  (:import (java.nio.file Files)))


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
