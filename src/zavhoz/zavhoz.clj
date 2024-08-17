(ns zavhoz.zavhoz
  (:gen-class)
  (:require [zavhoz.password]
            [zavhoz.backup]
            [zavhoz.db]
            [cli-matic.core :as cli]))

;; TODO
;; - бэкап директорий и файлов
;;   - рекурсивные бэкапы
;;   - архивирование
;; - подсказки по структуре
;;   - CLI формат работы
;;   - информация по текущим бэкапам


;; TODO
;; сделать пользователю сессию для паролей
(def configuration
  {:name "Zavhoz"
   :description "CLI master of file and passwords"
   :version "1.0"
   :subcommands
   (concat zavhoz.password/commands
           #_zavhoz.backup/commands)
   })

(defn -main
  [& args]
  (zavhoz.db/init-db!)
  (cli/run-cmd args configuration))
