(ns puppetserver-memmeasure.scenarios.catalog-multiple-nodes-one-jruby-one-environment
  (:require [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-schemas]
            [schema.core :as schema]
            [me.raynes.fs :as fs]
            [clojure.tools.logging :as log])
  (:import (java.io File)
           (com.puppetlabs.puppetserver JRubyPuppet)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(def LocalScenarioConfig
  {:num-containers schema/Int
   :num-catalogs-per-node schema/Int
   :environment-name schema/Str
   :environment-timeout memmeasure-schemas/EnvironmentTimeout
   :node-names [schema/Str]})

(schema/defn ^:always-validate run-catalog-multiple-nodes-one-jruby-one-environment-step
  :- memmeasure-schemas/StepRuntimeData
  [jruby-puppet :- JRubyPuppet
   mem-output-run-dir :- File
   step-base-name :- schema/Str
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [environment-name node-names]} :- LocalScenarioConfig
   iter :- schema/Int
   _]
  (doseq [node-name node-names]
    (util/get-catalog jruby-puppet
                      (fs/file mem-output-run-dir
                               (str
                                step-base-name
                                "-"
                                iter
                                "-"
                                node-name
                                "-catalog.json"))
                      node-name
                      environment-name
                      (format "role::by_size::%s" node-name)))
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-catalog-multiple-nodes-one-jruby-one-environment-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Compile a catalog 'num-catalogs' number of times"
  [{:keys [environment-name
           environment-timeout
           num-catalogs] :- memmeasure-schemas/ScenarioConfig}
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name (format "catalog-multiple-nodes-one-jruby-one-env-timeout-%s"
                               environment-timeout)]
    (util/with-jruby-puppet
     jruby-puppet
     jruby-puppet-config
     (log/infof "Using environment timeout: %s"
                (.getSetting jruby-puppet
                             "environment_timeout"))
     (scenario/run-scenario-body-over-steps
      (partial run-catalog-multiple-nodes-one-jruby-one-environment-step
               jruby-puppet
               mem-output-run-dir
               step-base-name)
      step-base-name
      mem-output-run-dir
      scenario-context
      {:num-containers 1
       :num-catalogs-per-node num-catalogs
       :environment-name environment-name
       :environment-timeout environment-timeout
       :node-names ["small" "empty"]}
      (range num-catalogs)))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  [scenario-config :- memmeasure-schemas/ScenarioConfig]
  [{:name
    (format
     "compile multiple catalogs in same environment with environment timeout %s"
     (:environment-timeout scenario-config))
    :fn run-catalog-multiple-nodes-one-jruby-one-environment-scenario}])
