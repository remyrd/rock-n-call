(ns rock-n-call.ui.core
  (:gen-class)
  (:require [cljfx.api :as fx]
            [clojure.java.io :as io]
            [rock-n-call.ui.events :as e]
            [rock-n-call.ui.views :as v]
            [clojure.core.cache :as cache])
  (:import [java.lang System]))


(defn start!
  "Starts the cljfx app.
  Handler: `e/handler-fn`
  Effects:
  - `:generate-sheet` `e/generate-sheet`
  - `:pagerduty` `e/pagerduty-handler`
  - `:change-dir` `e/change-dir`
  Root component: `v/root`

  Immediately dispatches `e/handler-fn` with `::e/initialize-pd-data`"
  []
  (let [config-path (str (System/getProperty "user.home") "/.rock-n-call")
        base-config {:path       config-path
                     :output-dir (str (System/getProperty "user.home") "/Documents")
                     :token      ""
                     :timezone   "CET"}
        *state      (atom (fx/create-context {:config     (or (when (.exists (io/file config-path))
                                                                (read-string (slurp config-path)))
                                                              base-config)
                                              :show-token false
                                              :status     "Enter a valid Pagerduty token and press START"}
                                             cache/lru-cache-factory))
        handler     (:handler (fx/create-app *state
                                             :event-handler e/handler-fn
                                             :desc-fn (fn [_]
                                                        {:fx/type v/root})
                                             :effects {:generate-sheet  e/generate-sheet
                                                       :choose-pto-file e/choose-pto-file
                                                       :pagerduty       e/pagerduty-handler
                                                       :change-dir      e/change-dir}))]
    (handler {:event-type ::e/initialize-pd-data})
    ))

(defn -main
  "Entry point calling `start!`.
  Do not call this method when testing, as it sets up the framework with `javafx.application.Platform/setImplicitExit` true"
  [& _]
  (javafx.application.Platform/setImplicitExit true)
  (start!))

(comment
  (start!))
