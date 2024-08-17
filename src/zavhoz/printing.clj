(ns zavhoz.printing
  (:require [clojure.pprint :as pp]))

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

(def agree? true)
(defmacro with-agree! [& body]
  `(do
     (when agree? (wrap! "You want to proceed?"))
     (print!
       (when (= "y" (read-line))
         (wrap! "Processing...")
         ~@body))))

(defn table! [header seq*]
  (wrap! header)
  (pp/print-table seq*))
