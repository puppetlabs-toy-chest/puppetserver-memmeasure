(ns puppetserver-memmeasure.scenarios.catalog-one-jruby-one-environment
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

(schema/defn ^:always-validate run-catalog-one-node-one-jruby-one-environment-step
  :- memmeasure-schemas/StepRuntimeData
  [jruby-puppet :- JRubyPuppet
   mem-output-run-dir :- File
   step-base-name :- schema/Str
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [environment-name node-names] :- memmeasure-schemas/ScenarioConfig}
   iter :- schema/Int
   _]
  (doseq [node-name node-names]
    (log/infof "Compiling catalog %d for node %s"
               (inc iter)
               node-name)
    (util/get-catalog jruby-puppet
                      (fs/file mem-output-run-dir
                               (str
                                step-base-name
                                "-node-"
                                node-name
                                "-catalog-"
                                (inc iter)
                                ".json"))
                      node-name
                      environment-name
                      (format "role::by_size::%s" node-name)))
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-catalog-one-jruby-one-environment-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Compile a catalog 'num-catalogs' number of times for a single environment
  and JRubyPuppet."
  [{:keys [environment-name
           environment-timeout
           node-names
           num-catalogs]} :- memmeasure-schemas/ScenarioConfig
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name "catalog-one-jruby-one-environment"]
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
       :num-environments 1
       :environment-name environment-name
       :environment-timeout environment-timeout
       :node-names node-names}
      (range num-catalogs)))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  []
  [{:name "compile catalogs in one jruby and environment"
    :fn run-catalog-one-jruby-one-environment-scenario}])
