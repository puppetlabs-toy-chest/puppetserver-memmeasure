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
   step-base-name :- schema/Str
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [environment-name nodes] :- memmeasure-schemas/ScenarioConfig}
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   iter :- schema/Int
   _]
  (doseq [{:keys [name expected-class-in-catalog]} nodes]
    (log/infof "Compiling catalog %d for node %s"
               (inc iter)
               name)
    (util/get-catalog jruby-puppet
                      (fs/file mem-output-run-dir
                               (str
                                step-base-name
                                "-node-"
                                name
                                "-catalog-"
                                (inc iter)
                                ".json"))
                      name
                      environment-name
                      jruby-puppet-config
                      expected-class-in-catalog))
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-catalog-one-jruby-one-environment-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Compile a catalog 'num-catalogs' number of times for a single environment
  and JRubyPuppet."
  [{:keys [environment-name
           environment-timeout
           nodes
           num-catalogs]} :- memmeasure-schemas/ScenarioConfig
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name "catalog-one-jruby-one-environment"]
    (util/with-jruby-puppet
     jruby-puppet
     jruby-puppet-config
     (scenario/run-scenario-body-over-steps
      (partial run-catalog-one-node-one-jruby-one-environment-step
               jruby-puppet)
      step-base-name
      mem-output-run-dir
      scenario-context
      {:num-containers 1
       :num-catalogs num-catalogs
       :num-environments 1
       :environment-name environment-name
       :environment-timeout environment-timeout
       :nodes nodes}
      jruby-puppet-config
      (range num-catalogs)))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  []
  [{:name "compile catalogs in one jruby and environment"
    :fn run-catalog-one-jruby-one-environment-scenario}])
