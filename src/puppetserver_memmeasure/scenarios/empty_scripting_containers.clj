(ns puppetserver-memmeasure.scenarios.empty-scripting-containers
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
   _]
  {:context
   (update
    scenario-context
    :jrubies
    conj
    {:container (util/create-scripting-container jruby-puppet-config)
     :jruby-puppet nil})})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-empty-scripting-containers-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Create a number of 'empty' JRuby ScriptingContainers.  The number created
   corresponds to the 'num-containers' parameter.  Memory measurements are
   taken after each container is created."
  [num-containers :- schema/Int
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   _
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (scenario/run-scenario-body-over-steps
   (partial run-empty-scripting-containers-step
            jruby-puppet-config)
   "create-container"
   mem-output-run-dir
   scenario-context
   (range num-containers)))
