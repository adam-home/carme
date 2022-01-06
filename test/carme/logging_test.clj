(ns carme.logging-test
  (:require [clojure.test :refer :all]
            [carme.logging :refer :all]))


(deftest test-will-log
  (testing "Check that the expected logging will occur at different log levels"

   (set-log-level :error)
   (is (= true (will-log? :error)))
   (is (= false (will-log? :info)))
   (is (= false (will-log? :debug)))

   (set-log-level :info)
   (is (= true (will-log? :error)))
   (is (= true (will-log? :info)))
   (is (= false (will-log? :debug)))

   (set-log-level :debug)
   (is (= true (will-log? :error)))
   (is (= true (will-log? :info)))
   (is (= true (will-log? :debug)))))
