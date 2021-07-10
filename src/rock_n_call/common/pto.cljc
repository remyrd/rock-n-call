(ns rock-n-call.common.pto
  (:require [dk.ative.docjure.spreadsheet :as xls]
            [clojure.string :as str]))

(defn format-row
  "Formats row data into the context of a timesheet"
  [row]
  (-> row
      (update :employeeid #(re-find #"7.*" %))
      (update :lastname #(-> % (str/split #" ") first)) ;; hispanic surnames...
      (assoc :employee (->> ((juxt :firstname :lastname) row)
                            (str/join "." )
                            str/lower-case))
      (dissoc :firstname :lastname)))

(defn file->maps
  "Reads a the first sheet of an .xls(x) file containing PTO information.
  Extracts the rows into maps."
  [file]
  (let [column-mapping {:A :employeeid
                        :B :lastname
                        :C :firstname
                        :E :status
                        :G :comment
                        :H :date
                        :I :days}]
    (->> (xls/load-workbook file)
         xls/sheet-seq
         first ;; first sheet - always
         (xls/select-columns column-mapping)
         rest ;; omit first row - contains titles
         (filter #(#{"Approved"} (:status %)))
         (map format-row))))

(comment
  (-> "abc" (str/split #" ") first)
  (filter #(re-find #"remy.*" (:employee %)) (file->maps "example.xlsx"))
  (group-by :employee (file->maps "example.xlsx")))

