(ns puppetserver-memmeasure.scenarios.basic-scripting-containers
  (:require [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-schemas]
            [schema.core :as schema])
  (:import (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn ^:always-validate run-empty-scripting-containers-step
  :- memmeasure-schemas/StepRuntimeData
  [jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   scenario-context :- memmeasure-schemas/ScenarioContext
   _
   _
   _]
  {:context
   (update
    scenario-context
    :jrubies
    conj
    {:container (util/create-scripting-container jruby-puppet-config)
     :jruby-puppet nil})})

(schema/defn ^:always-validate run-initialize-puppet-in-jruby-containers-step
  :- memmeasure-schemas/StepRuntimeData
  [jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   scenario-context :- memmeasure-schemas/ScenarioContext
   _
   iter :- schema/Int
   jruby-puppet-container :- memmeasure-schemas/JRubyPuppetContainer]
  {:context
   (update-in
    scenario-context
    [:jrubies iter :jruby-puppet]
    (constantly
     (util/initialize-puppet-in-container
      (:container jruby-puppet-container)
      jruby-puppet-config)))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-empty-scripting-containers-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Create a number of 'empty' JRuby ScriptingContainers.  The number created
   corresponds to the 'num-containers' parameter.  Memory measurements are
   taken after each container is created."
  [{:keys [num-containers]} :- memmeasure-schemas/ScenarioConfig
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (scenario/run-scenario-body-over-steps
   (partial run-empty-scripting-containers-step
            jruby-puppet-config)
   "create-container"
   mem-output-run-dir
   scenario-context
   {:num-containers num-containers}
   (range num-containers)))

(schema/defn ^:always-validate run-initialize-puppet-in-jruby-containers-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Initialize each of the ScriptingContainers found in the incoming
  'scenario-context' with Ruby Puppet code.  Memory measurements are
   taken after each container is initialized."
  [_
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [jrubies (:jrubies scenario-context)]
    (scenario/run-scenario-body-over-steps
     (partial run-initialize-puppet-in-jruby-containers-step
              jruby-puppet-config)
     "initialize-puppet-in-container"
     mem-output-run-dir
     scenario-context
     {:num-containers (count jrubies)}
     jrubies)))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  [_]
  [{:name "create empty scripting containers"
    :fn run-empty-scripting-containers-scenario}
   {:name "initialize puppet into scripting containers"
    :fn run-initialize-puppet-in-jruby-containers-scenario}])
