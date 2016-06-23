(ns puppetserver-memmeasure.scenarios.single-catalog-compile
  (:require [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-schemas]
            [schema.core :as schema]
            [me.raynes.fs :as fs])
  (:import (java.io File)
           (com.puppetlabs.puppetserver JRubyPuppet)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn ^:always-validate run-single-catalog-compile-step
  :- memmeasure-schemas/StepRuntimeData
  [jruby-puppet :- JRubyPuppet
   mem-output-run-dir :- File
   step-base-name :- schema/Str
   scenario-context :- memmeasure-schemas/ScenarioContext
   iter :- schema/Int
   _]
  (util/get-catalog jruby-puppet
                    (fs/file mem-output-run-dir
                             (str
                              step-base-name
                              "-"
                              iter
                              "-catalog.json"))
                    "role::by_size::small")
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-single-catalog-compile-env-timeout-zero-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  ;;TODO update
  "Create a number of 'empty' JRuby ScriptingContainers.  The number created
   corresponds to the 'num-containers' parameter.  Memory measurements are
   taken after each container is created."
  [num-catalogs :- schema/Int
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name "single-catalog-compile-env-timeout-zero"]
    (util/set-env-timeout! (:master-conf-dir jruby-puppet-config) 0)
    (util/with-jruby-puppet
     jruby-puppet
     jruby-puppet-config
     (println (.getSetting jruby-puppet "environment_timeout"))
     (scenario/run-scenario-body-over-steps
      (partial run-single-catalog-compile-step
               jruby-puppet
               mem-output-run-dir
               step-base-name)
      step-base-name
      mem-output-run-dir
      scenario-context
      (range num-catalogs)))))

(schema/defn ^:always-validate run-single-catalog-compile-env-timeout-unlimited-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  ;;TODO update
  "Create a number of 'empty' JRuby ScriptingContainers.  The number created
   corresponds to the 'num-containers' parameter.  Memory measurements are
   taken after each container is created."
  [num-catalogs :- schema/Int
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name "single-catalog-compile-env-timeout-unlimited"]
    (util/set-env-timeout! (:master-conf-dir jruby-puppet-config) "unlimited")
    (util/with-jruby-puppet
     jruby-puppet
     jruby-puppet-config
     (println (.getSetting jruby-puppet "environment_timeout"))
     (scenario/run-scenario-body-over-steps
      (partial run-single-catalog-compile-step
               jruby-puppet
               mem-output-run-dir
               step-base-name)
      step-base-name
      mem-output-run-dir
      scenario-context
      (range num-catalogs)))))
