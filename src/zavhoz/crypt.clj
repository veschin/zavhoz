(ns zavhoz.crypt
  (:require [clojure.string :as str]))

(def alphabet (map char (range 32 127)))

(defn generate-cipher-map [keyword]
  (let [unique-keyword (str/join (distinct keyword))
        remaining-chars (remove (set unique-keyword) alphabet)]
    (zipmap alphabet (concat unique-keyword remaining-chars))))

(defn encrypt [plaintext keyword]
  (let [cipher-map (generate-cipher-map keyword)]
    (apply str (map cipher-map plaintext))))

(defn decrypt [ciphertext keyword]
  (let [cipher-map (generate-cipher-map keyword)
        reverse-cipher-map (into {} (map (fn [[k v]] [v k]) cipher-map))]
    (apply str (map reverse-cipher-map ciphertext))))
