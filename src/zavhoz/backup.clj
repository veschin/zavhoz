(ns zavhoz.backup
  (:require [clojure.java.io :as io]
            [zavhoz.printing :as p]
            [toucan2.core :as t2]
            [clojure.pprint :as pp]))

;; TODO
;; - бэкап директорий и файлов
;;   - рекурсивные бэкапы
;;   - архивирование
(def backup-dir
  (-> (System/getProperty "user.home")
      (str "/.config/zavhoz/zavhoz_backups/.keep_me")
      (doto (io/make-parents))
      (io/file)
      (.getParent)))

(declare commands
         backup-zip!
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
;; TODO:
;; я могу сохранить только путь без начального /
;; значит при восстановлении бэкапа надо будет его обязательно добавлять
;; TODO:
;; Пока что все работает без регулярок,
;; но надо бы их завезти потом
;; TODO:
;; есть смысл перевести все хранение в gzip формат
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
            cp!               (fn [from to]
                                (->> (io/make-parents)
                                     (doto (io/file (str to "/" from)))
                                     (io/copy (io/file from))))
            db-save!          (fn [] (let [date (-> "yyyy-MM-dd"
                                                    (java.text.SimpleDateFormat.)
                                                    (.format (java.util.Date.)))]
                                       (if (t2/exists? :file :path path-abs)
                                         (t2/query {:update :file
                                                    :set {:path path-abs :updated_at date}
                                                    :where [:= :path path-abs]})
                                         (t2/insert! :file {:path path-abs :updated_at date} ))))]
        (if dir?
          (do
            (doseq [file  files-after-regex
                    :when (-> file .isDirectory not)]
              (cp! file backup-dir)
              (println (format "+ %s" (.getName file)))
              (swap! counter inc))
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

(defn forget-files! [{[path] :_arguments}]
  (let [path*       (str backup-dir "/" path)
        files-count (some->> path*
                             (io/file)
                             (file-seq)
                             (remove #(.isDirectory %))
                             (not-empty)
                             (count))
        counter     (atom 0)
        rm!         (fn rm*
                      [path]
                      (when (.isDirectory (io/file path))
                        (->> path (io/file) (.listFiles) (run! rm*)))
                      (when (-> path (io/file) (.isDirectory) (not))
                        (swap! counter inc)
                        (->> path
                             (io/file)
                             (.getName)
                             (format "- %s")
                             (println)))
                      (io/delete-file path))]
    (p/wrap! (format "Forget '%s'" path))
    (p/with-agree!
      (when (and (-> path* io/file .exists)
                 (t2/exists? :file :path path))
        (rm! path*)
        (t2/delete! :file :path path)
        (when files-count
          (format "Forgotten %s/%s" @counter files-count))))))

(comment
  (do (with-redefs [read-line (constantly "y")]
        (mem-files! {:_arguments ["/home/veschin/.config/bspwm" nil]}))
      (forget-files! {:_arguments ["/home/veschin/.config/bspwm" nil]}))
  (mem-refresh! {:_arguments []})
  (files-info {:_arguments ["/home/veschin/.config/bspwm" nil]})

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
    :runs        forget-files!}])
