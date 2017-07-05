(ns puppetserver-memmeasure.scenarios.basic-scripting-containers
  (:require [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.services.jruby-pool-manager.impl.jruby-internal :as jruby-internal]
            [puppetlabs.services.jruby-pool-manager.jruby-schemas :as jruby-schemas]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-puppet-schemas]
            [puppetlabs.services.jruby.jruby-puppet-core :as jruby-puppet-core]
            [schema.core :as schema]
            [clojure.tools.logging :as log])
  (:import (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn ^:always-validate run-empty-scripting-containers-step
  :- memmeasure-schemas/StepRuntimeData
  [_
   _
   scenario-context :- memmeasure-schemas/ScenarioContext
   _
   jruby-config :- jruby-schemas/JRubyConfig
   _
   _
   _]
  {:context
   (update
    scenario-context
    :jrubies
    conj
    {:container (jruby-internal/create-scripting-container jruby-config)
     :jruby-puppet nil})})

(schema/defn ^:always-validate run-initialize-puppet-in-jruby-containers-step
  :- memmeasure-schemas/StepRuntimeData
  [_
   _
   scenario-context :- memmeasure-schemas/ScenarioContext
   _
   _
   jruby-puppet-config :- jruby-puppet-schemas/JRubyPuppetConfig
   iter :- schema/Int
   jruby-puppet-container :- memmeasure-schemas/JRubyPuppetContainer]
  {:context
   (update-in
    scenario-context
    [:jrubies iter :jruby-puppet]
    (constantly
     (util/initialize-puppet-in-container
      jruby-puppet-config
      (:container jruby-puppet-container))))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-empty-scripting-containers-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Create a number of 'empty' JRuby ScriptingContainers.  The number created
   corresponds to the 'num-containers' parameter.  Memory measurements are
   taken after each container is created."
  [{:keys [num-containers]} :- memmeasure-schemas/ScenarioConfig
   jruby-config :- jruby-schemas/JRubyConfig
   jruby-puppet-config :- jruby-puppet-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [results
        (scenario/run-scenario-body-over-steps
         run-empty-scripting-containers-step
         "create-container"
         mem-output-run-dir
         scenario-context
         {:num-containers num-containers}
         jruby-config
         jruby-puppet-config
         (range num-containers))]
    (when-let [first-container (some-> results
                                       (get-in [:context :jrubies])
                                       first
                                       :container)]
      (log/infof "ScriptingContainer Ruby version info - %s"
                 (.getSupportedRubyVersion first-container))
      (log/infof "ScriptingContainer Compile Mode - %s"
                 (.getCompileMode first-container)))
    results))

(schema/defn ^:always-validate run-initialize-puppet-in-jruby-containers-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Initialize each of the ScriptingContainers found in the incoming
  'scenario-context' with Ruby Puppet code.  Memory measurements are
   taken after each container is initialized."
  [_
   jruby-config :- jruby-schemas/JRubyConfig
   jruby-puppet-config :- jruby-puppet-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [jrubies (:jrubies scenario-context)]
    (scenario/run-scenario-body-over-steps
     run-initialize-puppet-in-jruby-containers-step
     "initialize-puppet-in-container"
     mem-output-run-dir
     scenario-context
     {:num-containers (count jrubies)}
     jruby-config
     jruby-puppet-config
     jrubies)))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  []
  [{:name "create empty scripting containers"
    :fn run-empty-scripting-containers-scenario}
   {:name "initialize puppet into scripting containers"
    :fn run-initialize-puppet-in-jruby-containers-scenario}])
