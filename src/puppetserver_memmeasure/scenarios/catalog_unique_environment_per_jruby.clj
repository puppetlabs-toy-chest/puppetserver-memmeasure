(ns puppetserver-memmeasure.scenarios.catalog-unique-environment-per-jruby
  (:require [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.services.jruby-pool-manager.jruby-schemas :as jruby-schemas]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-puppet-schemas]
            [schema.core :as schema]
            [me.raynes.fs :as fs]
            [clojure.tools.logging :as log])
  (:import (java.io File)
           (com.puppetlabs.puppetserver JRubyPuppet)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private

(schema/defn ^:always-validate run-catalog-unique-environment-per-jruby-step
  :- memmeasure-schemas/StepRuntimeData
  [environment-names :- [schema/Str]
   step-base-name :- schema/Str
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [nodes num-catalogs] :- memmeasure-schemas/ScenarioConfig}
   _
   jruby-puppet-config :- jruby-puppet-schemas/JRubyPuppetConfig
   iter :- schema/Int
   jruby-puppet :- JRubyPuppet]
  (let [environment-name (nth environment-names iter)]
    (doseq [{:keys [name expected-class-in-catalog]} nodes
            catalog-ctr (range num-catalogs)]
      (log/infof "Compiling catalog %d for node %s and env %s in container %d"
                 (inc catalog-ctr)
                 name
                 environment-name
                 (inc iter))
      (util/get-catalog jruby-puppet
                        (fs/file mem-output-run-dir
                                 (str
                                  step-base-name
                                  "-name-"
                                  environment-name
                                  "-node-"
                                  name
                                  "-jruby-"
                                  (inc iter)
                                  "-catalog-"
                                  (inc catalog-ctr)
                                  ".json"))
                        name
                        environment-name
                        jruby-puppet-config
                        expected-class-in-catalog)))
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-catalog-unique-environment-per-jruby
  :- memmeasure-schemas/ScenarioRuntimeData
  "Perform catalog compilations in a unique environment configured per
  JRubyPuppet instance."
  [{:keys [environment-name num-environments]
    :as scenario-config} :- memmeasure-schemas/ScenarioConfig
   jruby-config :- jruby-schemas/JRubyConfig
   jruby-puppet-config :- jruby-puppet-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name "catalog-unique-environment-per-jruby"]
    (util/with-environments
     environments
     num-environments
     environment-name
     (:master-code-dir jruby-puppet-config)
     (util/with-jruby-puppets
      jruby-puppets
      num-environments
      jruby-config
      jruby-puppet-config
      (scenario/run-scenario-body-over-steps
       (partial run-catalog-unique-environment-per-jruby-step environments)
       step-base-name
       mem-output-run-dir
       scenario-context
       (assoc scenario-config :num-containers num-environments)
       jruby-config
       jruby-puppet-config
       jruby-puppets)))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  []
  [{:name "compile catalogs in a unique environment per jruby"
    :fn run-catalog-unique-environment-per-jruby}])
