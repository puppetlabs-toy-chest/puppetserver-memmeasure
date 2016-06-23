(ns puppetserver-memmeasure.scenarios.initialize-puppet-in-jruby-containers
  (:require [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-schemas]
            [schema.core :as schema])
  (:import (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn ^:always-validate run-initialize-puppet-in-jruby-containers-step
  :- memmeasure-schemas/StepRuntimeData
  [jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   scenario-context :- memmeasure-schemas/ScenarioContext
   iter :- schema/Int
   jruby :- memmeasure-schemas/JRubyPuppetScenarioEntry]
  {:context
   (update-in
    scenario-context
    [:jrubies iter :jruby-puppet]
    (constantly
     (util/initialize-puppet-in-container
      (:container jruby)
      jruby-puppet-config)))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-initialize-puppet-in-jruby-containers-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Initialize each of the ScriptingContainers found in the incoming
  'scenario-context' with Ruby Puppet code.  Memory measurements are
   taken after each container is initialized."
  [jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (scenario/run-scenario-body-over-steps
   (partial run-initialize-puppet-in-jruby-containers-step
            jruby-puppet-config)
   "initialize-puppet-in-container"
   mem-output-run-dir
   scenario-context
   (:jrubies scenario-context)))
