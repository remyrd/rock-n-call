(ns rock-n-call.ui.events
  (:require [clojure.pprint :refer [pprint]]
            [rock-n-call.common.pagerduty :as pd]
            [rock-n-call.common.time :as rnct]
            [rock-n-call.common.printer :as printer]
            [rock-n-call.ui.utils :as utils]
            [cljfx.api :as fx])
  (:import [javafx.stage DirectoryChooser]
           [javafx.event ActionEvent]
           [javafx.scene Node]
           [java.awt Desktop]
           [java.io File]))

(defn access-link
  "Side effecting link opener.
  Used to either open a file or a folder given
  a path in `destination`"
  [^String destination]
  (future
    (try
      (.open (Desktop/getDesktop) (File. destination))
      (catch Exception e
        (.printStackTrace e)))))

(defmulti handler-fn
  "Our cljfx instance registers this multimethod as the event handler.
  Accepts a map with the `:event-type` key to match the dispatch method.
  The map of effect definitions can be found in `rock-n-call.ui.core/start!`
  Methods:
  - `::print-everything` Pretty prints the whole metadata
  - `::default` Pretty prints the `:fx/event` key
  - `::change-dir` calls the `change-dir` effect
  - `::toggle-show-token` toggles the `:show-token` state key
  - `::initialize-pd-data` dispatches the `:pagerduty` `::initialize` effect
  - `::save-pd-data-to-state` Expects a `:schedules` key whose value is the list of schedules fetched from PD.
    Puts initialized pagerduty data to the `:schedules` state key
  - `::open-file` calls `access-link` with whatever is under `:f` as parameter
  - `::choose-team` dispatches the `:pagerduty` `::load-team` effect. Updates the `:chosen-team` state key.
  - `::put-team-to-state` expects an extra `:team` key. Populates the `:team` key in state with a map of employee-name->oncall-times.
    Populates `:selected-ep` with the first of any found escalation policies.
  - `::set-escalation-policy` Expects extra key `:escalation-policy`.
    Triggered when multiple escalation policies are available and user selects one.
  - `::edit-config-field` Edits field given as `text-key` to `:config` in state and dispatches `::save-config`
  - `::update-employee` Expects extra keys `:ename` (employee name) `:ekey` (either `:employeeid` or `:manager`)
    Dispatches `::edit-config-field` acting on the `:employees` key.
    Every employee looks like {\"John Doe\" {:employeeid 420 :manager \"Mike Fake\"}}
  - `::save-config` Spits the state config to the config file
  - `::sheet-dispatch` Arranges the data from the state before dispatching the `:generate-sheet` effect.
  - `::update-status` Updates the `:status` key in state with the given `:message` key, whose content is displayed at the top of the canvas.
  - `::add-recent-file` Appends a given file under `:f` (through `::edit-config-field`)
    to the config on the [`:config` `:teams` `:teamname` `:recent-files`] path
  - `::flip-active-employee` Expects extra keys `:ename` (employee name) `:teamname`.
    The config contains under each team a map of team-member->active? (string->bool) under `:members`.
    Updates the `:teams` key in the config by flipping the value under [`teamname` `:members` `ename`].
    missing/nil -> true
    false -> true
    true -> false
  "
  :event-type)

(defmethod handler-fn ::print-everything [e]
  (pprint e))

(defmethod handler-fn ::default [{:keys [fx/event]}]
  (pprint event))

(defmethod handler-fn ::change-dir [{:keys [^ActionEvent fx/event]}]
  {:change-dir {:event event}})

(defmethod handler-fn ::toggle-show-token [{:keys [fx/context]}]
  {:context (fx/swap-context context update :show-token not)})

(defmethod handler-fn ::initialize-pd-data [{:keys [fx/context]}]
  (if (seq (fx/sub-val context #(get-in % [:config :token])))
    {:dispatch {:event-type ::update-status
                :message "Fetching teams. Please wait"}
     :pagerduty {:event-type ::initialize
                 :token (fx/sub-val context #(get-in % [:config :token]))}}
    {:dispatch {:event-type ::update-status
                :message "Please enter a valid Pagerduty Token"}}))

(defmethod handler-fn ::save-pd-data-to-state [{:keys [fx/context schedules]}]
  {:context (fx/swap-context context assoc :schedules schedules)
   :dispatch {:event-type ::update-status
              :message (format "Loaded %d teams."
                               (->> schedules
                                    (mapcat :teams)
                                    distinct
                                    count))}})

(defmethod handler-fn ::open-file [{:keys [f fx/context]}]
  (access-link f)
  {:context context})

(defmethod handler-fn ::choose-team [{:keys [fx/event fx/context]}]
  (let [schedules (fx/sub-val context :schedules)
        team-schedules (filter (partial pd/contains-team? event) schedules)
        timezone (fx/sub-val context #(get-in % [:config :timezone]))]
    {:context (fx/swap-context context #(assoc %
                                               :chosen-team event
                                               :status (format "Status: Loading %s" (:summary event))))
     :pagerduty {:event-type ::load-team
                 :token (fx/sub-val context #(get-in % [:config :token]))
                 :timezone timezone
                 :schedules team-schedules}} ))

(defmethod handler-fn ::put-team-to-state [{:keys [fx/context team]}]
  {:context (fx/swap-context context merge {:team team
                                            :selected-ep ((comp :escalation_policy
                                                             first
                                                             val
                                                             first) team)})
   :dispatch {:event-type ::update-status
              :message "Loaded Team"}})

(defmethod handler-fn ::set-escalation-policy [{:keys [fx/event fx/context escalation-policy]}]
  {:context (fx/swap-context context assoc :selected-ep (if escalation-policy
                                                          escalation-policy
                                                          event))})

(defmethod handler-fn ::edit-config-field [{:keys [fx/event text-key fx/context]}]
  {:context (fx/swap-context context assoc-in [:config text-key] event)
   :dispatch {:event-type ::save-config}})

(defmethod handler-fn ::update-employee [{:keys [fx/event ename ekey fx/context]}]
  (let [employees (fx/sub-val context #(get-in % [:config :employees]))]
    {:dispatch {:event-type ::edit-config-field
                :text-key :employees
                :fx/event (assoc-in employees [ename ekey] event)}}))

(defmethod handler-fn ::save-config [{:keys [fx/context]}]
  (let [config (fx/sub-val context :config)]
    (spit (:path config) config)))

(defmethod handler-fn ::sheet-dispatch [{:keys [fx/context]}]
  (let [config (fx/sub-val context :config)
        teamdates (fx/sub-val context :team)
        selected-ep (fx/sub-val context :selected-ep)
        dates (into {}
                    (map (juxt key #(utils/schedules->rows selected-ep (val %)))
                         teamdates))
        teamname (fx/sub-val context #(get-in % [:chosen-team :summary]))
        output-path  (str (:output-dir config)
                          (format "/%s-%s.xls"
                                  (or teamname "")
                                  (rnct/format-now->str "YYYY-MM")))]
    {:generate-sheet {:employees (->> (map (juxt identity
                                                 #(get-in config [:employees %]))
                                           (keys teamdates))
                                      (filter #(utils/available? (first %) teamname config))
                                      (into {})
                                      (map (juxt key #(merge (val %)
                                                             {:dates (get dates (key %))})))
                                      (into {}))
                      :output-path output-path}}))

(defmethod handler-fn ::update-status [{:keys [fx/context message]}]
  {:context (fx/swap-context context assoc :status (format "Status: %s" message))})

(defmethod handler-fn ::add-recent-file [{:keys [fx/context f]}]
  (let [chosen-team (fx/sub-val context :chosen-team)
        teams (fx/sub-val context #(get-in % [:config :teams]))]
    [[:dispatch {:event-type ::update-status
                 :message (format  "Generated %s" f)}]
     [:dispatch {:event-type ::edit-config-field
                 :text-key :teams
                 :fx/event (update-in teams [(:summary chosen-team) :recent-files] concat [f])}]]))

(defmethod handler-fn ::flip-active-employee [{:keys [fx/context ename teamname fx/event]}]
  (let [teams (fx/sub-val context #(get-in % [:config :teams]))]
    {:dispatch {:event-type ::edit-config-field
                :text-key :teams
                :fx/event (assoc-in teams [teamname :members ename] event)}}))

(defn change-dir
  "Effect to handle output directory configuration.
  Coerces event as `javafx.eventActionEvent`
  Dispatches the `::edit-config-field` event when complete, editing `:output-dir`"
  [{:keys [^ActionEvent event]} d!]
  (fx/on-fx-thread
   (let [window (.getWindow (.getScene ^Node (.getTarget event)))
         chooser (doto (DirectoryChooser.)
                   (.setTitle "Open Directory"))]
     (when-let [directory (.showDialog chooser window)]
       (d! {:event-type ::edit-config-field
            :fx/event (.getAbsolutePath directory)
            :text-key :output-dir})))))

(defn generate-sheet
  "Effect to handle sheet generation.
  `d!` is the dispatch function to handle further events.
  Calls `printer/export-xls` with `args` asynchronously.
  If no exception is thrown, dispatches `::add-recent-file`.
  Otherwise notifies so through `::update-status`"
  [args d!]
  (future
    (try
      (printer/export-xls args)
      (d! {:event-type ::add-recent-file
           :f (:output-path args)})
      (catch Exception e
        (println e)
        (d! {:event-type ::update-status
             :message "Error generating the Sheet..."})))))

(defmulti pagerduty-handler
  "Multimethod used to handle the `:pagerduty` effect with calls to the pagerduty API.
  Accepts a map containing `:event-type` key to map the method.
  `pagerduty-handler` registers as an effect with `cljfx`, which means
  it is called by the framework with 2 arguments: an `args` map and a dispatch
  function `d!` as second argument. `d!` then calls whatever event
  Methods:
  - `::default` pretty prints the args
  - `::initialize` -> Tries to call `pd/get-schedules`. If successful, dispatches `::save-pd-data-to-state`
  - `::load-team` -> Calls `pd/get-oncalls` with given args. Dispatches `::put-team-to-state`"
  :event-type)

(defmethod pagerduty-handler :default [e _]
  (pprint e))

(defmethod pagerduty-handler ::initialize [args d!]
  (future
    (try
      (d! {:event-type ::save-pd-data-to-state
           :schedules (pd/get-schedules (:token args))})
      (catch Exception e
        (println e)
        (d! {:event-type ::update-status
             :message "Error retrieving teams... Check your token"})))))

(defmethod pagerduty-handler ::load-team [args d!]
  (future (d! {:event-type ::put-team-to-state
               :team (pd/get-oncalls args)})))


(comment
  (let [config {:employees {"ABC" {:ename "S"
                                   :manager "T"}
                            "BCD" {:ename "B"
                                   :manager "SOS"}}}
        team {"ABC" [{:date 1 :escalation_policy {:id 1}}
                     {:date 2 :escalation_policy {:id 2}}]
              "BCD" [{:date 2 :escalation_policy {:id 1}}
                     {:date 3 :escalation_policy {:id 4}}]}]
    (->> (map (juxt identity
                    #(get-in config [:employees %]))
              (keys team))
        (into {})
        (map (juxt key #(merge (val %)
                               {:dates (get team (key %))})))
         )))
