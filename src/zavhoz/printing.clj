(ns zavhoz.printing
  (:require [clojure.pprint :as pp]))

(def supress? false)
(def val? false)

(defn wrap! [message]
  (when-not supress?
    (if val?
      message
      (println (format "--\n%s\n--" message)))))

(defn progress! [message]
  (when-not supress?
    (println message)))

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

(def agree? false)
(defmacro with-agree! [& body]
  `(do
     (when-not agree? (wrap! "You want to proceed?"))
     (print!
       (when (= "y" (if agree? "y" (read-line)))
         (wrap! "Processing...")
         ~@body))))

(defn table! [header seq*]
  (wrap! header)
  (when-not supress?
    (pp/print-table seq*)))
