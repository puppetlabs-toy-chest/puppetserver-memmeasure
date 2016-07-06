(ns puppetserver-memmeasure.scenarios.catalog-group-by-jruby
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

(schema/defn ^:always-validate run-catalog-group-by-jruby-step
  :- memmeasure-schemas/StepRuntimeData
  [mem-output-run-dir :- File
   step-base-name :- schema/Str
   environment-names :- [schema/Str]
   scenario-context :- memmeasure-schemas/ScenarioContext
   {:keys [node-names num-catalogs] :- memmeasure-schemas/ScenarioConfig}
   iter :- schema/Int
   jruby-puppet :- JRubyPuppet]
  (doseq [environment-name environment-names
          node-name node-names
          catalog-ctr (range num-catalogs)]
    (log/infof "Compiling catalog %d for node %s and env %s in container %d"
               (inc catalog-ctr)
               node-name
               environment-name
               iter)
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

(schema/defn ^:always-validate run-catalog-group-by-jruby-scenario
  :- memmeasure-schemas/ScenarioRuntimeData
  "Perform catalog compilations, grouping captured memory statistics by each
  JRubyPuppet processed.  All of the catalog compilations for each node and
  environment are performed for one JRuby before each memory snapshot is taken."
  [{:keys [environment-name
           num-containers
           num-environments]
    :as scenario-config} :- memmeasure-schemas/ScenarioConfig
   jruby-puppet-config :- jruby-schemas/JRubyPuppetConfig
   mem-output-run-dir :- File
   scenario-context :- memmeasure-schemas/ScenarioContext]
  (let [step-base-name "catalog-group-by-jruby"]
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
       (partial run-catalog-group-by-jruby-step
                mem-output-run-dir
                step-base-name
                environments)
       step-base-name
       mem-output-run-dir
       scenario-context
       scenario-config
       jruby-puppets)))))

(schema/defn ^:always-validate scenario-data :- [memmeasure-schemas/Scenario]
  []
  [{:name "compile catalogs, grouping by jruby"
    :fn run-catalog-group-by-jruby-scenario}])
