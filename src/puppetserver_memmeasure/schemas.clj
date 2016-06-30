(ns puppetserver-memmeasure.schemas
  (:require [schema.core :as schema])
  (:import (clojure.lang IFn)
           (com.puppetlabs.puppetserver.jruby ScriptingContainer)
           (com.puppetlabs.puppetserver JRubyPuppet)))

(def EnvironmentTimeout
  (schema/enum 0 "0" "unlimited"))

(def Scenario
  {:name schema/Str
   :fn IFn})

(def ScenarioConfig
  {:num-containers schema/Int
   :num-catalogs schema/Int
   :environment-timeout EnvironmentTimeout
   :node-name schema/Str})

(def JRubyPuppetScenarioEntry
  {:container ScriptingContainer
   :jruby-puppet (schema/maybe JRubyPuppet)})

(def ScenarioContext
  {:jrubies [JRubyPuppetScenarioEntry]
   schema/Keyword schema/Any})

(def StepRuntimeData
  {:context ScenarioContext
   (schema/optional-key :results) {schema/Keyword schema/Any}})

(def StepResult
  {:name schema/Str
   :mem-inc-over-previous-step schema/Int
   :mem-used-after-step schema/Int
   schema/Keyword schema/Any})

(def ScenarioResult
  {:mean-mem-inc-after-first-step schema/Num
   :mean-mem-inc-after-second-step schema/Num
   :mem-at-scenario-start schema/Int
   :mem-at-scenario-end schema/Int
   :mem-inc-for-first-step schema/Int
   :mem-inc-for-scenario schema/Int
   :std-dev-mem-inc-after-first-step schema/Num
   :std-dev-mem-inc-after-second-step schema/Num
   :steps [StepResult]
   schema/Keyword schema/Any})

(def ScenarioResultWithName
  {:name schema/Str
   :config {schema/Keyword schema/Any}
   :results ScenarioResult})

(def ScenariosResult
  {:mem-inc-for-all-scenarios schema/Int
   :mem-used-after-last-scenario schema/Int
   :mem-used-before-first-scenario schema/Int
   :scenarios [ScenarioResultWithName]})

(def ScenarioRuntimeData
  {:config {schema/Keyword schema/Any}
   :context ScenarioContext
   :results ScenarioResult})

(def ScenariosRuntimeData
  {:context ScenarioContext
   :results ScenariosResult})
