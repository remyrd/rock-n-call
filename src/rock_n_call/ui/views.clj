(ns rock-n-call.ui.views
  (:require [cljfx.api :as fx]
            [rock-n-call.ui.events :as e]
            [rock-n-call.common.time :as rnct]))

(defn token-box
  "Section to configure pagerduty token.
  Subscribes to `:show-token` `:config->:token` `:schedules`
  When there schedules are empty (ie. `nil` in the state),
  The button will display 'START', otherwise 'Reload teams'.
  The text (password) field triggers the `::e/edit-config-field` event for the `:token` key"
  [{:keys [fx/context]}]
  (let [show (fx/sub-val context :show-token)
        token (or (fx/sub-val context (comp :token :config)) "")
        schedules (fx/sub-val context :schedules)]
    {:fx/type :v-box
     :spacing 10
     :children [
                {:fx/type :h-box
                 :spacing 10
                 :children [{:fx/type :label
                             :text "Pagerduty token: "}
                            {:fx/type (if show
                                        :text-field
                                        :password-field)
                             :text token
                             :on-text-changed {:event-type ::e/edit-config-field
                                               :text-key :token}}
                            {:fx/type :button
                             :disable (= token "")
                             :text (if show
                                     "Hide"
                                     "Show")
                             :on-action {:event-type ::e/toggle-show-token}}
                            {:fx/type :combo-box
                             :value "Europe/Prague"
                             :items ["Europe/Prague" "US/Pacific" "US/Eastern" "US/Central" "UTC"]
                             :button-cell (fn [tz] {:text tz})
                             :cell-factory (fn [tz] {:text tz})
                             :on-value-changed {:event-type ::e/edit-config-field
                                                :text-key :timezone}}]}
                {:fx/type :h-box
                 :spacing 10
                 :children [{:fx/type :button
                             :text (if schedules "Reload teams" "START")
                             :on-action {:event-type ::e/initialize-pd-data}}]}]}))

(defn team-selection
  "Once `:schedules` are retrieved from pagerduty,
  displays the combo-box to choose a team."
  [{:keys [fx/context]}]
  (if-let [schedules (fx/sub-val context :schedules)]
    {:fx/type :combo-box
     :items (distinct (mapcat :teams schedules))
     :value "Choose a team"
     :button-cell (fn [team] {:text (:summary team)})
     :cell-factory (fn [team] {:text (:summary team)})
     :on-value-changed {:event-type ::e/choose-team}}
    {:fx/type :label
     :text "No teams loaded yet"}))

(defn escalation-policy-selection
  "Sometimes teams have multiple escalation policies.
  If this is the case, this component will display a combo-box to let the user choose.
  Choosing an escalation policy triggers `::e/set-escalation-policy`"
  [{:keys [fx/context]}]
  (let [team (fx/sub-val context :team)
        escalation-policies (distinct (map :escalation_policy
                                           (mapcat val team)))]
    {:fx/type :combo-box
     :value "Choose an escalation policy"
     :visible (if (seq (rest escalation-policies)) true false)
     :items escalation-policies
     :button-cell (fn [ep] {:text (:summary ep)})
     :cell-factory (fn [ep] {:text (:summary ep)})
     :on-value-changed {:event-type ::e/set-escalation-policy}}))

(defn employee-view
  "Displays an employee form.
  `ename` is the employee name eg. \"Mike Fake\"
  `oncalls` are the team's resulting oncall data filtered for this employee.
  The employee name allows us to fetch his/her metadata from the `:config` (loaded in the state).
  Changes in the employee form trigger the `::e/update-employee` and `::e/flip-active-employee`"
  [{:keys [fx/context ename oncalls]}]
  (let [selected-ep (fx/sub-val context :selected-ep)
        employee (fx/sub-val context #(get-in % [:config :employees ename]))
        teamname (fx/sub-val context #(get-in % [:chosen-team :summary]))
        active (or (fx/sub-val context #(get-in % [:config :teams teamname :members ename])) false)
        hours (->> oncalls
                   (filter #(= (:id selected-ep)
                               (get-in % [:escalation_policy :id])))
                   (reduce #(+ % (rnct/interval->hours %2)) 0))]
    {:fx/type :v-box
     :spacing 5
     :children [{:fx/type :h-box
                 :children [{:fx/type :label
                             :text ename
                             :style {:-fx-font-weight "bold"}}
                            {:fx/type :check-box
                             :selected active
                             :on-selected-changed {:event-type ::e/flip-active-employee
                                                   :ename ename
                                                   :teamname teamname}}]}
                {:fx/type :h-box
                 :spacing 5
                 :visible active
                 :children [{:fx/type :label
                             :text "Personal No.: "}
                            {:fx/type :text-field
                             :text (or (:employeeid employee) "")
                             :on-text-changed {:event-type ::e/update-employee
                                               :ename ename
                                               :ekey :employeeid}}]}
                {:fx/type :h-box
                 :spacing 5
                 :visible active
                 :children [{:fx/type :label
                             :text "Manager: "}
                            {:fx/type :text-field
                             :text (or (:manager employee) "")
                             :on-text-changed {:event-type ::e/update-employee
                                               :ename ename
                                               :ekey :manager}}]}
                {:fx/type :label
                 :visible active
                 :text (str hours)}]}))

(defn team-display
  "Displays a team, composed of `employee-view` components
  and the button to generate sheets, which triggers `::e/sheet-dispatch`
  The `:team` key dereffed from the state is a map of employee-name->employee-oncalls"
  [{:keys [fx/context]}]
  (let [team (or (fx/sub-val context :team) [])]
    {:fx/type :v-box
     :spacing 10
     :visible (if (seq team) true false)
     :children [{:fx/type :h-box
                 :spacing 10
                 :children (map (fn [employee] {:fx/type employee-view
                                               :ename (key employee)
                                               :oncalls (val employee)})
                                team)}
                {:fx/type :button
                 :text "Generate Sheet"
                 :on-action {:event-type ::e/sheet-dispatch}}]}))

(defn output-dir-section
  "Lets the user choose which directory the generated files will be saved to."
  [{:keys [fx/context]}]
  (let [output-dir (fx/sub-val context #(get-in % [:config :output-dir]))]
    {:fx/type :h-box
     :spacing 10
     :children [{:fx/type :label
                 :text (format "Save to: ")}
                {:fx/type :hyperlink
                 :text output-dir
                 :on-action {:event-type ::e/open-file :f output-dir}}
                {:fx/type :button
                 :text "Change directory"
                 :on-action {:event-type ::e/change-dir}}]}))

(defn recent-files-section
  "Shows the 5 most recently generated files, filtered by team"
  [{:keys [fx/context]}]
  (let [chosen-team (fx/sub-val context :chosen-team)
        files (fx/sub-val context #(get-in % [:config
                                              :teams
                                              (:summary chosen-team)
                                              :recent-files]))]
    {:fx/type :v-box
     :spacing 5
     :padding 10
     :visible (if (seq files) true false)
     :children (cons {:fx/type :label
                       :text "Recent Documents:"}
                      (take 5 (map (fn [f]
                                     {:fx/type :hyperlink
                                      :text f
                                      :on-action {:event-type ::e/open-file
                                                  :f f}})
                                   (or (reverse files) []))))}))

(defn root
  "Root component. Composes all other components, either directly or indirectly"
  [{:keys [fx/context]}]
  {:fx/type :stage
   :showing true
   :on-shown {:event-type ::e/initialize-pd-data}
   :on-close-request (fn [& _] (System/exit 0))
   :scene {:fx/type :scene
           :root {:fx/type :v-box
                  :spacing 10
                  :padding 10
                  :pref-width 760
                  :pref-height 540
                  :children [{:fx/type :label
                              :text (or (fx/sub-val context :status) "")}
                             {:fx/type output-dir-section}
                             {:fx/type token-box}
                             {:fx/type :h-box
                              :spacing 10
                              :children [{:fx/type team-selection}
                                         {:fx/type escalation-policy-selection}]}
                             {:fx/type team-display}
                             {:fx/type recent-files-section}]}}})

(comment
  [{:abc 12} (when false {:fx "234"})]
  (filter some? '(nil 1 2))
  (dissoc (:employees {:employees {0 {:foo "bar"}
                                   1 {:foo "baz"}}})
          1)
  (swap! (atom {:employees {0 {:foo "bar"}
                            1 {:foo "baz"}}})
         (comp (partial dissoc 1) :employees))

  )
