(ns rock-n-call.common.time-test
  (:require [clojure.test :refer [deftest is]]
            [rock-n-call.common.time :refer [format-date->str]]
            [cljc.java-time.local-date-time :as ldt]))


(deftest test-format-date->str
  (let [oct-23-2020 (ldt/of 2020 10 23 0 0 0 0)]
    (is (= "2020-10-23"
           (format-date->str {:format  "YYYY-MM-dd"
                              :date oct-23-2020}))
        "Format is provided")
    (is (= "23/10/2020"
           (format-date->str {:date oct-23-2020}))
        "Format not provided. Defaults to 'dd/MM/YYYY'")))
