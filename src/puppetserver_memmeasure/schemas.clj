(ns puppetserver-memmeasure.schemas
  (:require [schema.core :as schema])
  (:import (clojure.lang IFn)
           (com.puppetlabs.puppetserver.jruby ScriptingContainer)
           (com.puppetlabs.puppetserver JRubyPuppet)))

(def EnvironmentTimeout
  (schema/enum 0 "0" "unlimited"))

(def Node
  {:name schema/Str
   :expected-class-in-catalog (schema/maybe schema/Str)})

(def Scenario
  {:name schema/Str
   :fn IFn})

(def ScenarioConfig
  {:num-containers schema/Int
   :num-catalogs schema/Int
   :num-environments schema/Int
   :environment-name schema/Str
   :environment-timeout EnvironmentTimeout
   :nodes [Node]})

(def JRubyPuppetContainer
  {:container ScriptingContainer
   :jruby-puppet (schema/maybe JRubyPuppet)})

(def ScenarioContext
  {:jrubies [JRubyPuppetContainer]
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

(def SummarizedScenario
  {:mean-mem-inc-after-first-step schema/Num
   :mean-mem-inc-after-second-step schema/Num
   :mem-inc-for-first-step schema/Int
   :readable-mean-mem-inc-after-first-step schema/Str
   :readable-mean-mem-inc-after-second-step schema/Str
   :readable-mem-inc-for-first-step schema/Str
   :config {schema/Keyword schema/Any}})

(def SummarizedScenarios
  {schema/Keyword SummarizedScenario})

(def SummarizedScenarioNamespaces
  {schema/Keyword SummarizedScenarios})

(def DiffScenario
  {:base SummarizedScenario
   :compare SummarizedScenario
   :compare-mean-mem-inc-after-first-step-over-base schema/Num
   :compare-mean-mem-inc-after-second-step-over-base schema/Num
   :compare-mem-inc-for-first-step schema/Int
   :readable-compare-mean-mem-inc-after-first-step-over-base schema/Str
   :readable-compare-mean-mem-inc-after-second-step-over-base schema/Str
   :readable-mem-inc-for-first-step schema/Str})

(def DiffScenarios
  {schema/Keyword DiffScenario})

(def DiffScenarioNamespaces
  {schema/Keyword DiffScenarios})

(def FullDiffScenarioNamespaces
  {:base-file schema/Str
   :compare-file schema/Str
   :diff DiffScenarioNamespaces})
