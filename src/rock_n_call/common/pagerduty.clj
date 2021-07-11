(ns rock-n-call.common.pagerduty
  "Namespace to deal with the Pagerdutyt REST API"
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [rock-n-call.common.time :as rnct]))

(defn- pd-get!
  "Performs GET HTTP requests against api.pagerduty.com/`resource`
  Depending on the pagination, may iterate over itself
  Returns the parsed data for the given resource
  Applies `filter-fn` if provided"
  [{:keys [token query-params resource filter-fn]
    :or {query-params {}
         filter-fn identity}}]
  (println "Fetching " (name resource) "...")
  (let [url (str "https://api.pagerduty.com/" (when resource (name resource)))
        http-opts {:headers {"Authorization" (str "Token token=" token)}
                   :accept :json}
        responses (loop [responses [{:more true}]]
                    (if (:more (first responses))
                      (let [query-str (cond-> query-params
                                        (:offset (first responses)) (assoc :offset (+ (:offset (first responses))
                                                                                     (:limit (first responses))))
                                        :always http/query-string)
                            response @(http/get (str url "?" query-str)
                                                http-opts)]
                        (recur (cons 
                                (-> response
                                    :body
                                    (json/parse-string keyword))
                                responses)))
                      responses))]
    (->> responses
         (keep #(get % resource))
         (map filter-fn)
         (apply concat))))

(defn get-schedules
  "Calls the /schedules Pagerduty resource.
  Returns for each available schedule only `:team` and `:id` keys"
  [token]
  (->> (pd-get! {:resource :schedules
                :token token
                :query-params {:limit 100}
                :filter-fn (partial filter (comp not-empty :escalation_policies))})
       (map #(select-keys % [:teams :id]))))

(defn contains-team?
  "Does the map reference the provided team under `:teams`"
  [team schedule]
  (seq (filter #(= (:summary team) (:summary %))
               (:teams schedule))))

(defn get-oncalls
  "Calls the /oncalls Pagerduty resource.
  Splits and trims dates based on payable hours during the current month for a given set of `schedules`.
  Schedules SHOULD be filtered to represent only a particular team's.
  Eg. Monday from 3am-8pm becomes 3am-9am, 6pm-8pm.
  Groups such time intervals by `:user` (who was on call).
  Returns a map of user->oncalls"
  [{:keys [token timezone schedules ptos]}]
  (let [since (rnct/date->str (rnct/first-day-of-month))
        until (rnct/date->str (rnct/first-day-of-next-month))]
    (->> (pd-get! {:resource     :oncalls
                   :token        token
                   :query-params {"schedule_ids[]" (map :id schedules)
                                  :time_zone       timezone
                                  :since           since
                                  :until           until}})
         (filter #(= 1 (:escalation_level %)))
         (map #(select-keys % [:start :end :user :escalation_policy]))
         (map rnct/parse-dates)
         (map rnct/trim-dates)
         (mapcat #(rnct/interval->dates % ptos))
         (group-by #(get-in % [:user :summary]))
         (into {}))))

(comment
  (def token (slurp ".token"))
  (def schedules ( get-schedules token))
  (def team (first (pd-get! {:resource :teams
                             :token token
                             :query-params {:query "team"}})))
  (def team-schedules (filter (partial contains-team? team) schedules))
  (def team-oncalls (get-oncalls {:schedules team-schedules
                                  :token token
                                  :timezone "CET"}))
  (count (pd-get! {:resource :schedules
                   :token token
                   :query-params {:limit 100}
                   :filter-fn (partial filter (comp not-empty :escalation_policies))
                   }))
  )
