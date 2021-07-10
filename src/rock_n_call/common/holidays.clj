(ns rock-n-call.common.holidays
  (:require [tick.alpha.api :as t]))

(defn ->instant
  [year month day]
  (-> (t/date (format "%d-%02d-%02d" year month day))
      (t/at "00:00")
      (t/in "Europe/Prague")))

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
    (->instant year month day)))

(defn czech-holidays
  "Lists all holidays in a given year"
  [year]
  [(->instant year 1 1)
   (t/<< (easter year) (t/new-period 2 :days))
   (t/>> (easter year) (t/new-period 1 :days))
   (->instant year 5 1)
   (->instant year 5 8)
   (->instant year 7 5)
   (->instant year 7 6)
   (->instant year 9 28)
   (->instant year 10 28)
   (->instant year 11 17)
   (->instant year 12 24)
   (->instant year 12 25)
   (->instant year 12 26)])

(defn holiday?
  "Given an instant, return `true` if the date is a holiday in Czech republic
  Return `false` otherwise"
  [instant]
  (let [year (-> instant t/year t/int)]
    (some #(= (t/date %) (t/date instant)) (czech-holidays year))))

(comment
  (holiday? (->instant 2020 5 1))
  (holiday? (->instant 2020 5 2)))
