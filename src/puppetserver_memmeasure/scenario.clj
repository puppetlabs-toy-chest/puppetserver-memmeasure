(ns puppetserver-memmeasure.scenario
  (:require [puppetserver-memmeasure.util :as util]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-schemas]
            [clojure.tools.logging :as log]
            [schema.core :as schema])
  (:import (java.io File)
           (clojure.lang IFn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn ^:always-validate mem-after-step :- (schema/maybe schema/Int)
  "Get the memory used after the step returned by the supplied nth-fn in the
   supplied output map was executed.  Returns nil if no step data is available."
  [scenario-output :- memmeasure-schemas/ScenarioRuntimeData
   nth-fn :- IFn]
  (-> scenario-output
      :results
      :steps
      nth-fn
      :mem-used-after-step))

(schema/defn ^:always-validate mem-after-first-step
  :- (schema/maybe schema/Int)
  "Get the memory used after the first step in the supplied output map was
  executed.  Returns nil if no step data is available."
  [scenario-output :- memmeasure-schemas/ScenarioRuntimeData]
  (mem-after-step scenario-output first))

(schema/defn ^:always-validate mem-after-last-step
  :- (schema/maybe schema/Int)
  "Get the memory used after the last step in the supplied output map was
  executed.  Returns nil if no step data is available."
  [scenario-output :- memmeasure-schemas/ScenarioRuntimeData]
  (mem-after-step scenario-output last))

;; From https://github.com/clojure-cookbook/clojure-cookbook/blob/master/01_primitive-data/1-20_simple-statistics.asciidoc#solution
(schema/defn mean
  [coll :- (schema/pred coll?)]
  (let [sum (apply + coll)
        count (count coll)]
    (if (pos? count)
      (/ sum count)
      0)))

;; From https://github.com/clojure-cookbook/clojure-cookbook/blob/master/01_primitive-data/1-20_simple-statistics.asciidoc#discussion
(schema/defn standard-deviation
  [coll :- (schema/pred coll?)]
  (let [avg (mean coll)
        squares (for [x coll]
                  (let [x-avg (- x avg)]
                    (* x-avg x-avg)))
        total (count coll)]
    (if (pos? total)
      (-> (/ (apply + squares) total)
          (Math/sqrt))
      0)))

(schema/defn ^:always-validate run-scenario-body-over-steps*
  [body-fn :- IFn
   step-base-name :- schema/Str
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext
   steps-data :- (schema/pred coll?)
   mem-at-scenario-start :- schema/Int]
  (loop [iter 0
         acc {:context scenario-context
              :results {:mem-at-scenario-start mem-at-scenario-start
                        :mem-at-scenario-end mem-at-scenario-start
                        :mem-inc-for-first-step 0
                        :mem-inc-for-scenario 0
                        :mean-mem-inc-after-first-step 0
                        :mean-mem-inc-after-second-step 0
                        :std-dev-mem-inc-after-first-step 0
                        :std-dev-mem-inc-after-second-step 0
                        :steps []}}
         remaining-steps-data steps-data]
    (if-let [step-data (first remaining-steps-data)]
      (do
        (let [body-results (body-fn (:context acc)
                                    iter
                                    step-data)
              step-full-name (str step-base-name "-" iter)
              mem-size (util/take-yourkit-snapshot! mem-output-run-dir
                                                    step-full-name)
              mem-after-previous-step (if-let [last-mem
                                               (mem-after-last-step acc)]
                                        last-mem
                                        mem-at-scenario-start)
              mem-inc-over-previous-step (- mem-size
                                            mem-after-previous-step)]
          (recur (inc iter)
                 (-> acc
                     (assoc :context (:context body-results))
                     (update-in [:results :steps]
                                conj
                                (merge
                                 (:results body-results)
                                 {:name
                                  step-full-name
                                  :mem-used-after-step
                                  mem-size
                                  :mem-inc-over-previous-step
                                  mem-inc-over-previous-step})))
                 (rest remaining-steps-data))))
      acc)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-scenario
  :- memmeasure-schemas/ScenariosRuntimeData
  "Execute the supplied scenario-fn and aggregate memory measurement results.
  Callback to the :fn from the supplied scenario to obtain intermediate results.
  Three arguments are supplied for each callback:

  * jruby-puppet-config - The jruby-puppet configuration provided as a parameter
                          to this function.

  * mem-output-run-dir - The directory into which run output files should be
                         written, also provided as a parameter to this function.

  * context - The value of the :context key from the acc-results parameter
              provided as a parameter to this function.

  Each callback should return a map corresponding to the ScenarioRuntimeData
  schema."
  [jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   acc-results :- memmeasure-schemas/ScenariosRuntimeData
   scenario :- memmeasure-schemas/Scenario]
  (let [scenario-fn (:fn scenario)
        scenario-name (:name scenario)
        environment-timeout (:environment-timeout scenario)
        _ (log/infof "Running scenario: %s" scenario-name)
        _ (when environment-timeout
            (log/infof "Setting environment timeout: %s"
                       environment-timeout)
            (util/set-env-timeout!
             (:master-conf-dir jruby-puppet-config)
             environment-timeout))
        scenario-output (scenario-fn jruby-puppet-config
                                     mem-output-run-dir
                                     environment-timeout
                                     (:context acc-results))
        _ (schema/validate memmeasure-schemas/ScenarioRuntimeData
                           scenario-output)
        mem-used-after-last-step-in-scenario (mem-after-last-step
                                              scenario-output)]
    (-> acc-results
        (update-in [:results :mem-used-after-last-scenario]
                   #(or mem-used-after-last-step-in-scenario %))
        (update-in [:results :scenarios]
                   conj
                   {:name scenario-name
                    :results (:results scenario-output)})
        (assoc :context (:context scenario-output)))))

(schema/defn ^:always-validate run-scenarios
  :- memmeasure-schemas/ScenariosResult
  "Execute a vector of supplied scenarios in order and aggregate memory
  measurement results."
  [jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenarios :- [memmeasure-schemas/Scenario]]
  (let [mem-used-before-first-scenario
        (util/take-yourkit-snapshot! mem-output-run-dir "baseline")

        scenario-results
        (-> (partial run-scenario
                     jruby-puppet-config
                     mem-output-run-dir)
            (reduce
             {:context
              {:jrubies []}
              :results
              {:mem-inc-for-all-scenarios 0
               :mem-used-before-first-scenario mem-used-before-first-scenario
               :mem-used-after-last-scenario mem-used-before-first-scenario
               :scenarios []}}
             scenarios)
            :results)

        mem-used-after-last-scenario (util/take-yourkit-snapshot!
                                      mem-output-run-dir
                                      "final")]
    (-> scenario-results
        (assoc :mem-used-after-last-scenario mem-used-after-last-scenario)
        (assoc :mem-inc-for-all-scenarios (- mem-used-after-last-scenario
                                             mem-used-before-first-scenario)))))

(schema/defn ^:always-validate run-scenario-body-over-steps
  :- memmeasure-schemas/ScenarioRuntimeData
  "Callback to the supplied body-fn once for each instance in the supplied
  steps-data.  Three arguments are supplied for each callback:

  * context - Map of data populated by previous scenario steps.  This
              corresponds to the ScenarioContext schema.

  * ctr - Counter representing the current step being executed.  The counter
          value for the current step is 0.  The counter is incremented by
          1 for each subsequent step.

  * step-data - Current step-data entry among the items in the supplied
                steps-data.

  Each callback should return a map corresponding to the StepRuntimeData
  schema.  The value corresponding to the ':context' key is the new
  ScenarioContext that should be passed along to subsequent scenario steps.
  The value for the optional ':results' key will be merged with other data to
  produce the result for the scenario step.

  This function returns an aggregation of result data for each of the steps
  which are executed."
  [body-fn :- IFn
   step-base-name :- schema/Str
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext
   steps-data :- (schema/pred coll?)]
  (let [mem-at-scenario-start (util/take-yourkit-snapshot! mem-output-run-dir
                                                           (str
                                                            step-base-name
                                                            "-baseline"))
        scenario-output (run-scenario-body-over-steps*
                         body-fn
                         step-base-name
                         mem-output-run-dir
                         scenario-context
                         steps-data
                         mem-at-scenario-start)
        mem-following-first-step (or (mem-after-first-step scenario-output)
                                     mem-at-scenario-start)
        mem-following-last-step (or (mem-after-last-step scenario-output)
                                    mem-following-first-step)
        mem-inc-for-first-step (- mem-following-first-step
                                  mem-at-scenario-start)
        mem-incs-after-first-step (-> scenario-output
                                      (get-in [:results :steps])
                                      rest
                                      ((partial map
                                                :mem-inc-over-previous-step)))
        mem-inc-for-scenario (- mem-following-last-step
                                mem-at-scenario-start)
        mem-incs-after-second-step (rest mem-incs-after-first-step)]
    (-> scenario-output
        (assoc-in [:results :mem-at-scenario-end] mem-following-last-step)
        (assoc-in [:results :mem-inc-for-scenario] mem-inc-for-scenario)
        (assoc-in [:results :mem-inc-for-first-step] mem-inc-for-first-step)
        (assoc-in [:results :mean-mem-inc-after-first-step]
                  (mean mem-incs-after-first-step))
        (assoc-in [:results :mean-mem-inc-after-second-step]
                  (mean mem-incs-after-second-step))
        (assoc-in [:results :std-dev-mem-inc-after-first-step]
                  (standard-deviation mem-incs-after-first-step))
        (assoc-in [:results :std-dev-mem-inc-after-second-step]
                  (standard-deviation mem-incs-after-second-step)))))
