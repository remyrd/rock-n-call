(ns rock-n-call.common.holidays
  (:require [cljc.java-time.zoned-date-time :as t]
            [cljc.java-time.local-date :as ld]))

(defn easter-sunday
  "Compute Easter Sunday given year - using Spencer Jones formula."
  [year]
  (let [a (mod year 19)
        b (quot year 100)
        c (mod year 100)
        d (quot b 4)
        e (mod b 4)
        f (quot (+ b 8) 25)
        g (quot (+ (- b f) 1) 3)
        h (mod (+ (* 19 a) (- b d g) 15) 30)
        i (quot c 4)
        k (mod c 4)
        l (mod (- (+ 32 (* 2 e) (* 2 i)) h k) 7)
        m (quot (+ a (* 11 h) (* 22 l)) 451)
        n (quot (+ h (- l (* 7 m)) 114) 31)
        p (mod (+ h (- l (* 7 m)) 114) 31)]
    {:month n :day (+ p 1)}))

(defn easter [year]
  (let [{:keys [month day]} (easter-sunday year)]
    (ld/of year month day)))


(defn holidays
  "Lists all holidays in a given year"
  [year]
  [(ld/of year 1 1)
   (ld/minus-days (easter year) 2)
   (ld/plus-days (easter year) 1)
   (ld/of year 5 1)
   (ld/of year 5 8)
   (ld/of year 7 5)
   (ld/of year 7 6)
   (ld/of year 9 28)
   (ld/of year 10 28)
   (ld/of year 11 17)
   (ld/of year 12 24)
   (ld/of year 12 25)
   (ld/of year 12 26)])

(defn holiday?
  "Given a datetime, return `true` if the date is a holiday in Czech republic
  Return `false` otherwise"
  [datetime]
  (let [date (ld/from datetime)
        year (ld/get-year date)]
    (some #(ld/equals % date) (holidays year))))

(comment
  (holiday? (ld/of 2020 5 1))
  (holiday? (ld/of 2020 5 2)))
