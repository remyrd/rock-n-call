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
                            (str/join " " )))
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
  (require '[cljc.java-time.zoned-date-time :as t])
  (-> "abc" (str/split #" ") first)
  (filter #(seq (:comment %)) (file->maps "example.xlsx"))
  (let [{abc 1.0
         bcd 0.5} (->>
                    (filter #(re-find #"Rem.*" (:employee %)) (file->maps "example.xlsx"))
                    (map #(select-keys % [:days :date]))
                    (map #(assoc % :date (.toInstant (:date %))))
                    (map #(assoc % :date (t/of-instant (:date %) (t/get-zone (t/now)))))
                    (map #(assoc % :date (t/to-local-date (:date %))))
                    (group-by :days)
                    (map (fn [[k v]] [k  (set (map :date v))]))
                    (into {}))]
    (bcd (t/to-local-date (t/now))))
  (#{(t/to-local-date (t/now))} (t/to-local-date (t/now)))
  (group-by :employee (file->maps "example.xlsx")))

