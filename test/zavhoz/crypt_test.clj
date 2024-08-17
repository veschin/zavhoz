(ns zavhoz.crypt-test
  (:require [zavhoz.crypt :as sut]
            [clojure.test :as t]))

(defn crypt-pipe [text keyphrase]
  (let [encrypted (sut/encrypt text keyphrase)]
    (t/is (= (sut/decrypt encrypted keyphrase) text))))

(t/deftest crypt-test
  (crypt-pipe "test" "s3cret")
  (crypt-pipe "!test_test!" "s3cret")
  (crypt-pipe "[test_test]" "s3cret")
  (crypt-pipe "[@te2st'1_1'te2st@]" "s3cret"))
