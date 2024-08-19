(ns zavhoz.zip
  (:require [clojure.java.io :as io])
  (:import [java.util.zip ZipEntry ZipInputStream ZipOutputStream]))

(defn unzip!
  [filename output-parent]
  (with-open [input (ZipInputStream. (io/input-stream filename))]
    (loop [entry (.getNextEntry input)]
      (when (some? entry)
        ;; NOTE: необходимо создать родительские директории для файла
        (->> (.getName entry)
             (format "%s/%s" output-parent)
             (io/file)
             (.getParentFile)
             (.mkdirs))
        ;; NOTE: записываем файл из архива и уходим на следующую итерацию
        (let [output (->> (.getName entry)
                          (format "%s/%s" output-parent)
                          (io/output-stream))]
          (io/copy input output)
          (.close output)
          (recur (.getNextEntry input)))))
    (.closeEntry input)))

(defn zip-folder*
  [input entry-name output & [meta-content]]
  (let [meta-path "/tmp/zavhoz_meta.edn"
        _         (when meta-content
                    (spit meta-path meta-content)
                    (zip-folder* (io/file meta-path) "zavhoz_meta.edn" output))]
    (if (.isDirectory input)
      ;; NOTE: рекурсивно собираем дочерние директории
      (loop [children (-> input .listFiles vec not-empty)]
        (when (not-empty children)
          (zip-folder* (first children)
                       (->> children first (.getName) (str entry-name "/"))
                       output)
          (recur (rest children))))
      ;; NOTE: пишем в зип
      (let [in    (io/input-stream input)
            entry (ZipEntry. entry-name)]
        (.putNextEntry output entry)
        (io/copy in output)
        (.closeEntry output)
        (.close in)
        (when (-> meta-path (io/file) (.exists))
          (io/delete-file (io/file meta-path)))))))

(defn zip-folder! [input output & [meta-content]]
  (with-open [output (ZipOutputStream. (io/output-stream output))]
    (zip-folder* (io/file input) "" output meta-content)))

(defn show-zip-content [input]
  (with-open [input (ZipInputStream. (io/input-stream input))]
    (let [content (loop [acc   []
                         entry (.getNextEntry input)]
                    (if (nil? entry)
                      acc
                      (recur (conj acc (.getName entry))
                             (.getNextEntry input))))]
        (.closeEntry input)
        content)))
