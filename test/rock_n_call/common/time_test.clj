(ns rock-n-call.common.time-test
  (:require [clojure.test :refer [deftest is]]
            [rock-n-call.common.time :as rnct]
            [tick.alpha.api :as tt]
            [cljc.java-time.local-date-time :as ldt]))


(deftest test-format-date->str
  (let [oct-23-2020 (ldt/of 2020 10 23 0 0 0 0)]
    (is (= "2020-10-23"
           (rnct/format-date->str {:format  "YYYY-MM-dd"
                              :date oct-23-2020}))
        "Format is provided")
    (is (= "23/10/2020"
           (rnct/format-date->str {:date oct-23-2020}))
        "Format not provided. Defaults to 'dd/MM/YYYY'")))


(deftest test-tt-date->string
  (is (= (rnct/tt-date->string (tt/date #inst "2020-02-01T03:55:00Z"))
         "01/02/2020")))


(deftest test-tt-first-day-of-month
  (is (= (tt/inst (rnct/tt-first-day-of-month 3 2022))
         #inst "2022-03-01T00:00:00+01:00")))

(deftest test-tt-parse-dates
  (is (= (rnct/tt-parse-dates {:start "2021-05-05T00:34:00-04:00"
                               :other :key
                               :end "2021-12-05T02:34:00-04:00"})
         {:start #inst "2021-05-05T00:34:00-04:00"
          :other :key
          :end #inst "2021-12-05T02:34:00-04:00"})))

(deftest test-tt-rounded-hour
  (is (= (rnct/tt-rounded-hour #inst "2021-12-05T02:04:00-04:00")
         #inst "2021-12-05T02:00:00-04:00"))
  (is (= (rnct/tt-rounded-hour #inst "2021-12-05T02:24:00-04:00")
         #inst "2021-12-05T02:30:00-04:00"))
  (is (= (rnct/tt-rounded-hour #inst "2021-12-05T02:34:00-04:00")
         #inst "2021-12-05T02:30:00-04:00"))
  (is (= (rnct/tt-rounded-hour #inst "2021-12-05T02:54:00-04:00")
         #inst "2021-12-05T03:00:00-04:00")))

(deftest test-tt-weekday-times-normal-oncall
  (is (= (rnct/tt-weekday-times #inst "2020-02-01T03:55:00Z"
                                #inst "2020-02-01T23:55:00Z"
                                nil)
         [{:tick/beginning #inst "2020-02-01T03:55:00Z"
           :tick/end #inst "2020-02-01T09:00:00Z"}
          {:tick/beginning #inst "2020-02-01T18:00:00Z"
           :tick/end #inst "2020-02-01T23:55:00Z"}]))
  (is (= (rnct/tt-weekday-times #inst "2020-02-01T13:55:00Z"
                                #inst "2020-02-01T23:55:00Z"
                                nil)
         [{:tick/beginning #inst "2020-02-01T18:00:00Z"
           :tick/end #inst "2020-02-01T23:55:00Z"}]))
  (is (= (rnct/tt-weekday-times #inst "2020-02-01T03:55:00Z"
                                #inst "2020-02-01T13:55:00Z"
                                nil)
         [{:tick/beginning #inst "2020-02-01T03:55:00Z"
           :tick/end #inst "2020-02-01T09:00:00Z"}])))

(deftest test-tt-weekday-times-no-pay
  (is (= (rnct/tt-weekday-times #inst "2020-02-01T13:55:00Z"
                                #inst "2020-02-01T15:55:00Z"
                                nil)
         [])))



