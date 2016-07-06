(ns puppetserver-memmeasure.scenarios.catalog-group-by-environment
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

(schema/defn ^:always-validate run-catalog-group-by-environment-step
  :- memmeasure-schemas/StepRuntimeData
  [mem-output-run-dir :- File
   step-base-name :- schema/Str
   jruby-puppets :- [JRubyPuppet]
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [node-names num-catalogs] :- memmeasure-schemas/ScenarioConfig}
   iter :- schema/Int
   environment-name :- schema/Str]
  (doseq [jruby-puppet jruby-puppets
          node-name node-names
          catalog-ctr (range num-catalogs)]
    (log/infof "Compiling catalog %d for node %s in container %d for env %s"
               (inc catalog-ctr)
               node-name
               (inc (.indexOf jruby-puppets jruby-puppet))
               environment-name)
    (util/get-catalog jruby-puppet
                      (fs/file mem-output-run-dir
                               (str
                                step-base-name
                                "-name-"
                                environment-name
                                "-node-"
                                node-name
                                "-jruby-"
                                (inc iter)
                                "-catalog-"
                                (inc catalog-ctr)
                                ".json"))
                      node-name
                      environment-name
                      (format "role::by_size::%s" node-name)))
  {:context scenario-context})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate run-catalog-group-by-environment-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Perform catalog compilations, grouping captured memory statistics by each
  environment processed.  All of the catalog compilations for each node and
  JRubyPuppet are performed for one environment before each memory snapshot
  is taken."
  [{:keys [environment-name
           num-containers
           num-environments]
    :as scenario-config} :- memmeasure-schemas/ScenarioConfig
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name "catalog-group-by-environment"]
    (util/with-environments
     environments
     num-environments
     environment-name
     (:master-code-dir jruby-puppet-config)
     (util/with-jruby-puppets
      jruby-puppets
      num-containers
      jruby-puppet-config
      (scenario/run-scenario-body-over-steps
       (partial run-catalog-group-by-environment-step
                mem-output-run-dir
                step-base-name
                jruby-puppets)
       step-base-name
       mem-output-run-dir
       scenario-context
       scenario-config
       environments)))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  []
  [{:name "compile catalogs, grouping by environment"
    :fn run-catalog-group-by-environment-scenario}])
