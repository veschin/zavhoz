(ns zavhoz.db
  (:require [clojure.java.jdbc :as jdbc]
            [toucan2.core :as t2]
            [methodical.core :as m]
            [clojure.java.io :as io]))

(def db-path (str (System/getProperty "user.home") "/.config/zavhoz/zavhoz_db"))
(def db-spec
  {:dbtype "sqlite"
   :dbname db-path})

(m/defmethod t2/do-with-connection :default
  [_connectable f]
  (t2/do-with-connection db-spec f))

(defn create-table! [table & fields]
  (->> fields
       (partition-all 2)
       (into [[:id "integer primary key autoincrement"]])
       (jdbc/create-table-ddl table)
       (jdbc/execute! db-spec)))

(defn init-db! []
  (do (io/make-parents db-path)
      (when-not (-> db-path io/file .exists)
        (create-table! :password
                       :name "text unique"
                       :encrypted :text
                       :keyphrase :text
                       :created_at :date)
        (create-table! :file :path :text :alias :text :cron :text))))

;; TODO:
;; команда для сброса базы данных

(comment
  #_(io/delete-file db-path)
  (t2/select :file)
  (t2/select :password)
  (t2/insert! :file {:path "/tmp"
                     :alias "Временная папка"
                     })
  )
