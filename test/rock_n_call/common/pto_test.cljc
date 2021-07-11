(ns rock-n-call.common.pto-test
  (:require [clojure.test :refer [deftest is]]
            [rock-n-call.common.pto :as pto]
            [dk.ative.docjure.spreadsheet :as xls]))

(deftest format-row
  (is (= (pto/format-row {:employeeid "00001757488"
                          :lastname   "Rojas"
                          :firstname  "Remy"})
         {:employeeid "757488"
          :employee   "Remy Rojas"})))


