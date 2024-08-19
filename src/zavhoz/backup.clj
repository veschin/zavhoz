(ns zavhoz.backup
  (:require [clojure.java.io :as io]
            [zavhoz.printing :as p]
            [zavhoz.zip :as zip]
            [toucan2.core :as t2]
            [clojure.string :as str]
            ))

; TODO
;; - бэкап директорий и файлов
;;   - рекурсивные бэкапы
;;   - архивирование
(def backup-dir
  (-> (System/getProperty "user.home")
      (str "/.config/zavhoz/zavhoz_backups/.keep_me")
      (doto (io/make-parents))
      (io/file)
      (.getParent)))

(declare
         backup-zip!
         backup-unzip!
         forget-files!
         restore-zip!
         )

(def files-info? true)
(defn files-info [{[path] :_arguments}]
  (when files-info?
    (p/try!
      (if (-> path io/file .exists)
        (some->> path
                 (io/file)
                 (file-seq)
                 (mapv #(->> % (.getName) (re-find #"\..*" )))
                 (filter some?)
                 (not-empty)
                 (frequencies)
                 (sort-by second >)
                 (mapv #(zipmap [:ext :count] %))
                 (p/table! (format "Files stat in '%s'" (-> path io/file .getAbsolutePath))))
        (p/wrap! (format "Dir '%s' doesn't exists" path))))))

(defn- simple-date []
  (-> "yyyy-MM-dd"
      (java.text.SimpleDateFormat.)
      (.format (java.util.Date.))))
;; TODO:
;; я могу сохранить только путь без начального /
;; значит при восстановлении бэкапа надо будет его обязательно добавлять
;; TODO:
;; Пока что все работает без регулярок,
;; но надо бы их завезти потом
;; TODO:
;; есть смысл перевести все хранение в gzip формат


;; TODO: нужно переработать эту функцию
(defn cp! [& [counter]]
  (fn cp
    ([from to] (cp from to (some-> from (io/file) file-seq)))
    ([_from to file-seq]
     (doseq [file  file-seq
             :when (-> file .isDirectory not)]
       (->> (io/make-parents)
            (doto (io/file (str to "/" file)))
            (io/copy (io/file file)))
       (p/progress! (format "+ %s" (.getName file)))
       (when counter (swap! counter inc))))))

(defn mem-files! [{[path regex] :_arguments}]
  (let [path     (io/file path)
        dir?     (.isDirectory path)
        path-abs (.getAbsolutePath path)]
    (if dir?
      (files-info {:_arguments [path]})
      (p/wrap! (format "Backup file '%s'?" path-abs)))
    (p/with-agree!
      (let [files             (-> path-abs io/file (file-seq))
            files-after-regex (if regex
                                (filter #(re-matches (re-pattern regex) (.getName %)) files)
                                files)
            counter           (atom 0)
            cp!               (cp! counter)
            db-save!          (fn [] (let [date (simple-date)]
                                       (if (t2/exists? :file :path path-abs)
                                         (t2/query {:update :file
                                                    :set {:path path-abs :updated_at date}
                                                    :where [:= :path path-abs]})
                                         (t2/insert! :file {:path path-abs :updated_at date} ))))]
        (if dir?
          (do
            (cp! path backup-dir files-after-regex)
            (db-save!)
            (->> files-after-regex
                 (remove #(.isDirectory %))
                 (count)
                 (format "Done %s/%s" @counter)))
          (do
            (cp! path-abs backup-dir)
            (db-save!)
            (format "Done for '%s'" path-abs)))))))

(defn mem-list [{_ :_arguments}]
  (let [memos (t2/select [:file :path :updated_at])]
    (-> "Current memos - "
        (str (count memos))
        (p/table! memos))))
;; TODO:
;; Пока что все работает без регулярок,
;; но надо бы их завезти потом
(defn mem-refresh! [{_ :_arguments}]
  (p/print!
    (with-redefs [files-info? false
                  p/agree?    false
                  read-line   (constantly "y")]
      (let [files   (t2/select :file)
            counter (atom 0)]
        (when (not-empty files)
          (doseq [{:keys [path]} files]
            (mem-files! {:_arguments [path]})
            (swap! counter inc))
          (format "Done refresh %s/%s" @counter (count files)))))))

(defn rm! [& [counter]]
  (fn rm*
    [path]
    (when (.isDirectory (io/file path))
      (->> path (io/file) (.listFiles) (run! rm*)))
    (when (-> path (io/file) (.isDirectory) (not))
      (when counter (swap! counter inc))
      (->> path
           (io/file)
           (.getName)
           (format "- %s")
           (p/progress!)))
    (io/delete-file path)))

(defn forget-files! [{[path] :_arguments}]
  (let [path*       (str backup-dir "/" path)
        files-count (some->> path*
                             (io/file)
                             (file-seq)
                             (remove #(.isDirectory %))
                             (not-empty)
                             (count))
        counter     (atom 0)
        rm!         (rm! counter)]
    (p/wrap! (format "Forget '%s'" path))
    (p/with-agree!
      (when (and (-> path* io/file .exists)
                 (t2/exists? :file :path path))
        (rm! path*)
        (t2/delete! :file :path path)
        (when files-count
          (format "Forgotten %s/%s" @counter files-count))))))

;; TODO:
;; подумать как добавить сюда SQL базу
(defn backup-zip! [{all? :all path :path}]
  (let [psize      (fn psize* [f]
                     (if (.isDirectory (io/file f))
                       (apply + (pmap psize* (.listFiles (io/file f))))
                       (.length (io/file f))))
        zip-path   (str "/tmp/bkp-"(simple-date))
        path       (if all? backup-dir path)
        something? (or (or path all?)
                       (some-> path (io/file) (.exists))
                       (some-> path (io/file) (file-seq) (rest) (not-empty) (boolean)))]
    (p/print!
      (when something?
        (let [meta (mapv (fn [{path :path upd :updated_at}]
                           {:path path :updated_at (str upd)})
                         (if all?
                           (t2/select [:file :path :updated_at])
                           (t2/select :file :path path)))]
          (zip/zip-folder! path zip-path meta)
          (format "Done backup to '%s' with size %.2f MB"
                  zip-path
                  (as-> zip-path p
                    (psize p)
                    (/ p 1024 1024)
                    (float p))))))))

(defn restore-zip! [{remem? :remem [path] :_arguments}]
  (when (some-> path io/file .exists)
    (let [tmp-folder "/tmp/.zavhoz_unzip"
          counter (atom 0)
          cp! (cp! counter)]
      (try
        (let [meta (some->> (str tmp-folder "/zavhoz_meta.edn") slurp read-string)]
          (zip/unzip! path tmp-folder)
          (p/table! "Will be restored" meta)
          (p/with-agree!
            (doseq [{path* :path} meta]
              (println ["from" (str tmp-folder path*) "to" path*])
              ;; TODO:
              ;; сейчас файлы не копируются, потому что сломаны пути
              ;; у точки назначения не верный путь
              ;; [from /tmp/.zavhoz_unzip/home/veschin/.ssh to /home/veschin/.ssh]
              (cp! (str tmp-folder path*) (-> path*
                                              (io/file)
                                              (file-seq)
                                              (mapv #(-> % .getAbsolutePath
                                                         (str/replace)))))
              (when remem?
                (with-redefs [p/supress? true
                              p/agree? true]
                  (mem-files! {:_arguments [path*]}))))
            (format "Done %s/%s files" @counter
                    (->> tmp-folder io/file file-seq
                         (remove #(.isDirectory %))
                         (rest)
                         (count)))))
        (finally (rm! tmp-folder))))))

(comment
  (.exists (io/file nil))
  (do (with-redefs [read-line (constantly "y")]
        (mem-files! {:_arguments ["/home/veschin/.config/bspwm" nil]}))
      (forget-files! {:_arguments ["/home/veschin/.config/bspwm" nil]}))
  (mem-refresh! {:_arguments []})
  (files-info {:_arguments ["/home/veschin/.config/bspwm" nil]})
  (with-redefs [p/agree? true]
    (restore-zip! {:_arguments ["/tmp/bkp-2024-08-19"] :remem true}))

  (zip/zip-folder! backup-dir "/tmp/b.zip" {:a 10})
  (zip/unzip! "/tmp/bkp-2024-08-19" "/tmp/test")

  (-> "/tmp/test/zavhoz_meta.edn" slurp read-string)

  (str/replace "/tmp/.zavhoz_unzip/home/veschin/.ssh" #"/tmp/.zavhoz_unzip" "")

  (t2/select :file)

  (t2/exists? :file :path "/home/veschin/.config/doom")
  )

(def commands
  [{:command     "files-info"
    :description "Statistics about files"
    :opts        [{:as     "Path to inspect"
                   :option "path"
                   :type   :string}]
    :runs        files-info}
   {:command     "mem-files"
    :description "Memoize files"
    :opts        [{:as     "Path to memoize"
                   :option "path"
                   :type   :string}
                  ;; TODO: регулярочки в некст релизе завезем
                  #_{:as     "Java regex"
                     :option "regex"
                     :type   :string}]
    :runs        mem-files!}
   {:command     "mem-list"
    :description "Memoize list"
    :opts        []
    :runs        mem-list}
   {:command     "mem-re"
    :description "Memoize refresh all links"
    :opts        []
    :runs        mem-refresh!}
   {:command     "forget-files"
    :description "Forget files"
    :opts        [{:as     "Path to forget"
                   :option "path"
                   :type   :string}]
    :runs        forget-files!}
   {:command     "bkp-zip"
    :description "Zip selected or all backups"
    :opts        [{:as     "Path to forget"
                   :option "path"
                   :type   :flag}
                  {:as     "All?"
                   :option "all"
                   :type   :flag}]
    :runs        backup-zip!}
   {:command     "restore-zip"
    :description "Resotore all files to File System and update ZavhozDB from zip"
    :opts        [{:as     "Path to zip"
                   :option "path"
                   :type   :string}
                  {:as     "Remember restored files?"
                   :option "remem"
                   :type   :flag}]
    :runs        restore-zip!}])
