(ns puppetserver-memmeasure.scenarios.basic-scripting-containers
  (:require [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-schemas]
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
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
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
  [_
   _
   scenario-context :- memmeasure-schemas/ScenarioContext
   _
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
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
  (let [results
        (scenario/run-scenario-body-over-steps
         run-empty-scripting-containers-step
         "create-container"
         mem-output-run-dir
         scenario-context
         {:num-containers num-containers}
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
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [jrubies (:jrubies scenario-context)]
    (scenario/run-scenario-body-over-steps
     run-initialize-puppet-in-jruby-containers-step
     "initialize-puppet-in-container"
     mem-output-run-dir
     scenario-context
     {:num-containers (count jrubies)}
     jruby-puppet-config
     jrubies)))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  []
  [{:name "create empty scripting containers"
    :fn run-empty-scripting-containers-scenario}
   {:name "initialize puppet into scripting containers"
    :fn run-initialize-puppet-in-jruby-containers-scenario}])
