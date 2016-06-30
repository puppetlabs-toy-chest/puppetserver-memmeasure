(ns puppetserver-memmeasure.scenarios.catalog-one-node-one-jruby-one-environment
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
   :num-catalogs schema/Int
   :environment-timeout memmeasure-schemas/EnvironmentTimeout
   :node-name schema/Str})

(schema/defn ^:always-validate run-catalog-one-node-one-jruby-one-environment-step
  :- memmeasure-schemas/StepRuntimeData
  [jruby-puppet :- JRubyPuppet
   mem-output-run-dir :- File
   step-base-name :- schema/Str
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [node-name] :- LocalScenarioConfig}
   iter :- schema/Int
   _]
  (util/get-catalog jruby-puppet
                    (fs/file mem-output-run-dir
                             (str
                              step-base-name
                              "-"
                              iter
                              "-catalog.json"))
                    node-name
                    (format "role::by_size::%s" node-name))
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-catalog-one-node-one-jruby-one-environment-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Compile a catalog 'num-catalogs' number of times for a specific node"
  [{:keys [environment-timeout
           node-name
           num-catalogs] :- memmeasure-schemas/ScenarioConfig}
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name (format "catalog-%s-one-jruby-one-env-timeout-%s"
                            node-name environment-timeout)]
    (util/with-jruby-puppet
     jruby-puppet
     jruby-puppet-config
     (log/infof "Using environment timeout: %s"
                (.getSetting jruby-puppet
                             "environment_timeout"))
     (scenario/run-scenario-body-over-steps
      (partial run-catalog-one-node-one-jruby-one-environment-step
               jruby-puppet
               mem-output-run-dir
               step-base-name)
      step-base-name
      mem-output-run-dir
      scenario-context
      {:num-containers 1
       :num-catalogs num-catalogs
       :environment-timeout environment-timeout
       :node-name node-name}
      (range num-catalogs)))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  [scenario-config :- memmeasure-schemas/ScenarioConfig]
  [{:name (format "compile the %s catalog with environment timeout %s"
                  (:node-name scenario-config)
                  (:environment-timeout scenario-config))
    :fn run-catalog-one-node-one-jruby-one-environment-scenario}])
