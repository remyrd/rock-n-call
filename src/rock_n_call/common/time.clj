(ns rock-n-call.common.time
  (:require [rock-n-call.common.holidays :as h]
            [tick.alpha.api :as t])
  (:import (java.time ZonedDateTime
                      Instant)))

(defn date->string
  [date format]
  (t/format (tick.format/formatter format) date))

(defn format-now->str
  [format]
  (date->string (t/in (t/instant) "Europe/Prague") format))

(defn first-day-of-month
  ([]
   (first-day-of-month (t/int (t/month))))
  ([month]
   (first-day-of-month month (t/int (t/year))))
  ([month year]
   (-> (t/date (format "%d-%02d-%02d" year month 1))
       (t/at "00:00")
       (t/in "Europe/Prague"))))

(defn first-day-of-next-month
  []
  (first-day-of-month (-> (t/month) t/int inc)))

(defn parse-dates
  [{:keys [start end] :as schedule}]
  (letfn [(parse [in]
            (-> in
                (ZonedDateTime/parse)))]
    (-> schedule
        (assoc :tick/beginning (parse start))
        (assoc :tick/end (parse end))
        (dissoc :start :end))))

(defn rounded-hour
  "Rounds time to HH:00, HH:30, or HH+1:00"
  [t]
  (let [truncated      (t/truncate t :hours)
        minute         (t/minute t)
        rounded-offset (cond-> 0
                         (> minute 14) (+ 30)
                         (> minute 44) (+ 30))]
    (-> truncated
        (t/>> (t/new-duration rounded-offset :minutes)))))

(defn trim-dates
  [{:tick/keys [beginning end] :as schedule}]
  (let [rounded-start (rounded-hour beginning)
        rounded-end   (rounded-hour end)]
    (assoc schedule
           :tick/beginning (t/max rounded-start (first-day-of-month))
           :tick/end (t/min rounded-end (first-day-of-next-month)))))

(defn beginning-of-next-day
  "Given a date, returns midnight aka 00:00 of the next day"
  [d]
  (t/>> (t/truncate d :days) (t/new-duration 1 :days)))

(defn weekday-times
  "Splits a daily interval into off-hours.
  Necessary for weekdays where oncall is not charged
  during working hours (9am - 6pm)"
  [beginning end]
  (let [midnight (-> beginning (t/truncate :days))
        oncall   {:tick/beginning beginning
                  :tick/end       end}
        work     {:tick/beginning (t/>> midnight (t/new-duration 9 :hours))
                  :tick/end       (t/>> midnight (t/new-duration 18 :hours))}]
    ;; https://www.juxt.land/tick/docs/index.html#_comparison_4
    (case (t/relation oncall work)
      (:during
       :starts
       :finishes)    []
      (:preceded-by
       :precedes)    [{:tick/beginning beginning
                       :tick/end       end}]
      (:meets
       :overlaps
       :finished-by) [{:tick/beginning (:tick/beginning oncall)
                       :tick/end       (:tick/beginning work)}]
      (:met-by
       :overlapped-by
       :started-by)  [{:tick/beginning (:tick/end work)
                       :tick/end       (:tick/end oncall)}]
      :contains      [{:tick/beginning (:tick/beginning oncall)
                       :tick/end       (:tick/beginning work)}
                      {:tick/beginning (:tick/end work)
                       :tick/end       (:tick/end oncall)}])))

(defn interval->dates
  "Splits a several-days interval into consecutive intervals
  of several hours during each day.
  Differenciates workdays and weekends/holidays"
  [{:tick/keys [beginning end] :as schedule}]
  (loop [current-dt beginning
         time-rows  []]
    (let [next-dt     (beginning-of-next-day current-dt)
          day-of-week (t/day-of-week current-dt)]
      (if (t/> end current-dt)
        ;; loop into next day
        (if (or (#{t/SATURDAY t/SUNDAY} day-of-week)
                (h/holiday? current-dt))
          (recur next-dt
                 (concat time-rows
                         (list (assoc schedule
                                      :tick/beginning current-dt
                                      :tick/end   (t/min end next-dt)))))
          (recur next-dt
                 (concat time-rows
                         (map (partial merge schedule)
                              (weekday-times current-dt
                                             (t/min end next-dt))))))
        ;; exit loop
        time-rows))))

(defn interval->hours
  [interval]
  (-> (t/divide-by (t/new-duration 30 :minutes) interval)
      count
      (/ 2)
      float))

(defn date->row
  "Given an interval, generates the corresponding row for the oncall sheet"
  [{:tick/keys [beginning end] :as interval}]
  (let [st (date->string beginning "HH:mm")
        en (date->string end "HH:mm")]
    (list (date->string beginning "dd/MM/YYYY")
          st
          (if (= en "00:00")
            "24:00"
            en)
          (interval->hours interval))))

(->> {:start "2021-07-05T00:34:00-04:00"
      :user  {:id "123"}
      :end   "2021-07-08T12:00:00-04:00"}
     parse-dates
     trim-dates
     interval->dates
     (map date->row)
     )

(comment
  (#{t/SATURDAY t/SUNDAY}
    (-> (t/instant)
        t/day-of-week))
  (t/day-of-week (t/instant))
  (->> {:start "2021-07-05T00:34:00-04:00"
        :user  {:id "123"}
        :end   "2021-07-08T12:00:00-04:00"}
       parse-dates
       trim-dates
       interval->dates
       (map date->row)
       )
  )

