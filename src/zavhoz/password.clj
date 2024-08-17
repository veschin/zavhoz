(ns zavhoz.password
  (:require [toucan2.core :as t2]
            [clojure.string :as str]
            [zavhoz.crypt :as crypt]))


;; TODO
;; - хранение паролей
;;   - дата создания
;;   - дата изменения
;;
;; добавить аргумент отключения pretty принтов
(declare
 remove-password)

(defn wrap! [message] (println (format "--\n%s\n--" message)))

(defmacro try! [& body]
  `(try ~@body
        (catch Exception e#
          (->> e#
               ex-message
               (format "Error: \n%s")
               (wrap!)
               ))))

(defmacro print! [& body]
  (let [p (fn [expr]
            (if expr
              (wrap! expr)
              (wrap! "Nothing")))]
    `(try! (~p (do ~@body)))))

(defmacro with-authz! [bindings & body]
  `(do
     (wrap! "Enter keyphrase")
     (print!
       (when-let ~bindings
         ~@body))))

(defn list-passwords [{_args :_arguments}]
  (print!
    (some->> :password
             (t2/select-fn-vec (fn [pass]
                                 (format "'%s' created at %s"
                                         (:name pass)
                                         (:created_at pass))))
             (not-empty)
             (str/join "\n"))))

(defn store-password! [{[name pass] :_arguments}]
  (with-authz! [key (read-line)]
    (t2/insert! :password {:name       name
                           :encrypted  (crypt/encrypt (or pass (str (random-uuid))) key)
                           :keyphrase  key
                           :created_at (-> "yyyy-MM-dd"
                                           (java.text.SimpleDateFormat.)
                                           (.format (java.util.Date.)))})
    (format "Password for '%s' stored" name)))

(defn get-password [{[name] :_arguments}]
  (with-authz! [key (read-line)]
   (let [{:keys [keyphrase encrypted]}
         (t2/select-one :password {:where [:= :name name]})
         pass (crypt/decrypt encrypted key)]
     (if (= key keyphrase)
       (format "Password for '%s' - %s" name pass)
       "Authz error"))))

(defn update-password! [{[name pass keyphrase*] :_arguments}]
  (with-authz! [key (read-line)]
    (let [{:keys [keyphrase]}
          (t2/select-one :password {:where [:= :name name]})]
      (if (= key keyphrase)
        (do (t2/query {:update :password
                       :set    {:encrypted (crypt/encrypt pass key)
                                :keyphrase (or keyphrase* keyphrase)}
                       :where  [:= :name name]})
          (format "Password for '%s' stored" name))
        "Authz error"))))

(defn remove-password! [{[name] :_arguments}]
  (with-authz! [key (read-line)]
    (let [{:keys [keyphrase]}
          (t2/select-one :password {:where [:= :name name]})]
      (if (= key keyphrase)
        (do (t2/delete! :password {:where [:= :name name]})
          (format "Password for '%s' deleted!" name))
        "Authz error"))))

(def commands
  [{:command     "pass-store"
    :description "Store password"
    :opts        [{:as     "Name of password"
                   :option "name"
                   :type   :string}
                  {:as     "Password"
                   :option "pass"
                   :type   :string}]
    :runs        store-password!}
   {:command     "pass-upd"
    :description "Update password"
    :opts        [{:as     "name"
                   :option "name"
                   :type   :string}
                  {:as     "Password"
                   :option "pass"
                   :type   :string}
                  {:as     "Keyphrase"
                   :option "keyphrase*"
                   :type   :string}]}
   {:command     "pass-rm"
    :description "Remove password"
    :opts        [{:as     "name"
                   :option "name"
                   :type   :string}]
    :runs        remove-password!}
   {:command     "pass-list"
    :description "List passwords"
    :opts        []
    :runs        list-passwords}
   {:command     "pass-get"
    :description "Get password"
    :opts        [{:as     "name"
                   :option "name"
                   :type   :string}]
    :runs        get-password}])

(comment


  )
