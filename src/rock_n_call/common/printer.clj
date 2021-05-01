(ns rock-n-call.common.printer
  (:require [dk.ative.docjure.spreadsheet :as docjure]))

(defn initialize-workbook
  [employees]
  (apply docjure/create-sparse-workbook
         (-> (mapcat (fn [[ename {:keys [employeeid manager]
                                 :or {employeeid 0
                                      manager ""}}]]
                       (list ename [["Number" employeeid]
                                    ["Name" ename]
                                    ["Manager" manager]
                                    ["Date", "Start", "End", "Total"]]))
                     employees)
             (conj [["personal no." "name" "MS 453" "MS 003"]] "Summary pro import"))))

(defn export-xls
  "Given a map of employee data and and output-path
  Builds an xls workbook, composed of a sheet per user and a summary"
  [{:keys [employees output-path]}]
  (let [workbook (initialize-workbook employees)
        summary-sheet (docjure/select-sheet "Summary pro import" workbook)
        style (docjure/create-cell-style! workbook {:background :pale_blue
                                                    :halign :left
                                                    :font {:bold true}})]
    (doseq [ename (keys employees)
            :let [sheet (docjure/select-sheet ename workbook)
                  {:keys [dates employeeid]} (get employees ename)
                  total (reduce #(+ (nth %2 3) %1) 0 dates)]]
      (docjure/add-rows! sheet dates)
      (docjure/add-row! sheet (list "" "" "Total: " total))
      (doseq [head-row (take 3 (docjure/row-seq sheet))]
        (docjure/set-cell-style! (first head-row) style))
      (docjure/set-row-style! (nth (docjure/row-seq sheet) 3) style)
      (docjure/add-row! summary-sheet (list employeeid ename total "0.00")))
    (docjure/set-row-style! (first (docjure/row-seq summary-sheet)) style)
    (docjure/save-workbook! output-path workbook)))

(comment (export-xls {:output-path "~/example.xls"
                         :employees {"Pat" {:employeeid 123
                                            :manager "Bob"
                                            :dates [["2020-10-11" "00:00" "14:00" 14]
                                                    ["2020-10-12" "00:00" "14:00" 14]]}
                                     "Mat" {:employeeid 122
                                            :manager "Ros"
                                            :dates [["2020-10-13" "00:00" "10:00" 10]
                                                    ["2020-10-15" "00:00" "10:00" 10]]}}}))
