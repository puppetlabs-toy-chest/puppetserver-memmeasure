(ns puppetserver-memmeasure.scenarios.catalog-one-node-multiple-jrubies-one-environment
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
   :environment-name schema/Str
   :environment-timeout memmeasure-schemas/EnvironmentTimeout
   :node-name schema/Str})

(schema/defn ^:always-validate run-catalog-one-node-multiple-jrubies-one-environment-step
  :- memmeasure-schemas/StepRuntimeData
  [mem-output-run-dir :- File
   step-base-name :- schema/Str
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [environment-name node-name num-catalogs] :- LocalScenarioConfig}
   iter :- schema/Int
   jruby-puppet :- JRubyPuppet]
  (dotimes [catalog-ctr num-catalogs]
    (log/infof "Compiling catalog %d for container %d"
               catalog-ctr
               iter)
    (util/get-catalog jruby-puppet
                      (fs/file mem-output-run-dir
                               (str
                                step-base-name
                                "-jruby-"
                                iter
                                "-"
                                catalog-ctr
                                "-catalog.json"))
                      node-name
                      environment-name
                      (format "role::by_size::%s" node-name)))
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-catalog-one-node-multiple-jrubies-one-environment-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Compile a catalog 'num-catalogs' number of times for a specific node in the
  same environment across 'num-containers' jrubies."
  [{:keys [environment-name
           environment-timeout
           node-name
           num-catalogs
           num-containers] :- memmeasure-schemas/ScenarioConfig}
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name (format "catalog-%s-multiple-jrubies-one-env-timeout-%s"
                               node-name
                               environment-timeout)]
    (util/with-jruby-puppets
     jruby-puppets
     num-containers
     jruby-puppet-config
     (scenario/run-scenario-body-over-steps
      (partial run-catalog-one-node-multiple-jrubies-one-environment-step
               mem-output-run-dir
               step-base-name)
      step-base-name
      mem-output-run-dir
      scenario-context
      {:num-containers num-containers
       :num-catalogs num-catalogs
       :environment-name environment-name
       :environment-timeout environment-timeout
       :node-name node-name}
      jruby-puppets))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  [scenario-config :- memmeasure-schemas/ScenarioConfig]
  [{:name (format (str "compile the %s catalog in multiple jrubies with "
                       "environment timeout %s")
                  (:node-name scenario-config)
                  (:environment-timeout scenario-config))
    :fn run-catalog-one-node-multiple-jrubies-one-environment-scenario}])
