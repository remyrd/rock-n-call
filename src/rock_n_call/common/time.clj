(ns rock-n-call.common.time
  (:require [cljc.java-time.zoned-date-time :as t]
            [cljc.java-time.temporal.temporal :as temp]
            [cljc.java-time.temporal.chrono-unit :as unit]
            [cljc.java-time.day-of-week :as dow]
            [cljc.java-time.format.date-time-formatter :as dtf]
            [rock-n-call.common.holidays :as h]))

(def now (t/now))

(defn format-date->str
  "Formats a `date` object into a string following a `format` string"
  [{:keys [format date] :or {format "dd/MM/YYYY"}}]
  (dtf/format (dtf/of-pattern format) date))

(defn format-now->str [format]
  (format-date->str {:format format
                     :date now}))

(defn date->str [date]
  (format-date->str {:date date}))

(defn first-day-of-month []
  (t/of (t/get-year now) (t/get-month-value now) 1
        0 0 0 0 (t/get-zone now)))

(defn first-day-of-next-month []
  (t/plus-months (first-day-of-month) 1))

(defn parse-dates [{:keys [start end] :as schedule}]
  (merge schedule {:start (t/parse start)
                   :end (t/parse end)}))

(defn rounded-hour
  "Rounds time to HH:00, HH:30, or HH+1:00"
  [datetime]
  (let [tdt (t/truncated-to datetime unit/hours)
        minutes (t/get-minute datetime)]
    (t/plus-minutes tdt (cond-> 0
                          (> minutes 14) (+ 30)
                          (> minutes 44) (+ 30)))))

(defn trim-dates [{:keys [start end] :as schedule}]
  (let [rounded-start (rounded-hour start)
        rounded-end (rounded-hour end)]
    (merge schedule {:start (if (t/is-after rounded-start (first-day-of-month))
                              rounded-start
                              (first-day-of-month))
                     :end (if (t/is-after rounded-end (first-day-of-next-month))
                            (first-day-of-next-month)
                            rounded-end)})))

(defn beginning-of-next-day
  "Given a date, returns midnight aka 00:00 of the next day"
  [date-time]
  (-> date-time
      (t/truncated-to unit/days)
      (t/plus-days 1)))

(defn weekday-times
  "Splits an daily interval into off-hours.
  Necessary for weekdays where oncall is not charged
  during working hours (9am - 6pm)"
  [start end half-day-ptos]
  (let [is-pto?    (and half-day-ptos
                        (half-day-ptos (t/to-local-date start))
                        (t/plus-hours (t/truncated-to start unit/days) 14))
        start-work (or is-pto?
                       (t/plus-hours (t/truncated-to start unit/days) 9))
        end-work   (t/plus-hours (t/truncated-to start unit/days) 18)]
    (filter some? [;; period between 0-9
                   (when (t/is-after start-work start)
                     {:start start
                      :pto   is-pto?
                      :end   (if (t/is-after end start-work)
                               start-work
                               end)})
                   ;; period between 18-24
                   (when (t/is-after end end-work)
                     {:start (if (t/is-after start end-work)
                               start
                               end-work)
                      :pto   is-pto?
                      :end   end})])))

(defn interval->dates
  "Splits a several-days interval into consecutive intervals
  of several hours during each day.
  Differenciates workdays and weekends/holidays"
  [{:keys [start end user] :as schedule} ptos]
  (let [{full-day-ptos 1.0
         half-day-ptos 0.5} (->> ptos
                                 ;; TODO transducer
                                 (filter #(= (:summary user) (:employee %)))
                                 (map #(select-keys % [:days :date]))
                                 (map #(assoc % :date (.toInstant (:date %))))
                                 (map #(assoc % :date (t/of-instant (:date %) (t/get-zone (t/now)))))
                                 (map #(assoc % :date (t/to-local-date (:date %))))
                                 (group-by :days)
                                 (map (fn [[k v]] [k  (set (map :date v))]))
                                 (into {}))]
    (loop [current-dt start
           time-rows  []]
      (let [next-dt       (beginning-of-next-day current-dt)
            full-day-pto? (and full-day-ptos
                               (full-day-ptos (t/to-local-date current-dt)))]
        (if (t/is-after end current-dt)
          ;; loop into next day
          (if (or (dow/equals dow/saturday (dow/from current-dt))
                  (dow/equals dow/sunday (dow/from current-dt))
                  (h/holiday? current-dt)
                  full-day-pto?)
            (recur next-dt
                   (concat time-rows
                           (list (merge schedule
                                        {:start current-dt
                                         :pto   full-day-pto?
                                         :end   (if (t/is-after end next-dt)
                                                  next-dt
                                                  end)} ))))
            (recur next-dt
                   (concat time-rows
                           (map (partial merge schedule)
                                (weekday-times current-dt
                                               (if (t/is-after end next-dt)
                                                 next-dt
                                                 end)
                                               half-day-ptos)))))
          ;; exit loop
          time-rows)))))

(defn interval->hours
  [{:keys [start end]}]
  (-> (temp/until start end unit/minutes)
      (/ 60)
      float))

(defn date->row
  "Given an interval, generates the corresponding row for the oncall sheet"
  [{:keys [start end pto] :as interval}]
  (let [time-format (dtf/of-pattern "HH:mm")
        date-format (dtf/of-pattern "dd/MM/YYYY")
        start-str   (dtf/format time-format start)
        end-str     (dtf/format time-format end)]
    (list (dtf/format date-format start)
          start-str
          (if (= end-str "00:00")
            "24:00"
            end-str)
          (interval->hours interval)
          (when pto "PTO"))))


(comment
  (require '[rock-n-call.common.pto :refer [file->maps]])
  (def ptos (file->maps "example2.xlsx"))
  (->> {:start "2021-07-01T00:34:00-02:00"
        :user  {:id "123" :summary "Remy Rojas"}
        :end   "2021-07-14T12:00:00-02:00"}
       parse-dates
       trim-dates
       (#(interval->dates % ptos))
       (map date->row))
  )
