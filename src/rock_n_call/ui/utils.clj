(ns rock-n-call.ui.utils
  (:require [rock-n-call.common.time :as rnct]))

(defn schedules->rows [selected-ep teamdates]
  (->> teamdates
       (filter #(= (:id selected-ep) (get-in % [:escalation_policy :id])))
       (map rnct/date->row)))

(defn available? [employee teamname config]
  (or (get-in config [:teams teamname :members employee]) false))
