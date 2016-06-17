(ns puppetserver-memmeasure.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.scenarios.empty-scripting-containers
             :as empty-scripting-containers]
            [puppetserver-memmeasure.scenarios.initialize-puppet-in-jruby-containers
             :as initialize-puppet-in-jruby-containers]
            [puppetlabs.puppetserver.cli.subcommand :as cli]
            [puppetlabs.services.jruby.jruby-puppet-core :as jruby-puppet-core]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.trapperkeeper.config :as tk-config]
            [clj-time.core :as clj-time-core]
            [clj-time.format :as clj-time-format]
            [me.raynes.fs :as fs]
            [schema.core :as schema])
  (:import (java.io File)))

(def default-output-dir "./target/mem-measure")
(def default-num-containers 4)

(schema/defn ^:always-validate create-output-run-dir! :- File
  "Create a run-specific directory under the supplied base directory.
  If the base directory were '/my/testdir', a subdirectory based on the current
  date/time when this function is run is created, e.g.,
  '/my/testdir/20160620T155254.905Z'"
  [base-output-dir :- schema/Str]
  (let [run-specific-subdir (clj-time-format/unparse
                             (clj-time-format/formatters :basic-date-time)
                             (clj-time-core/now))
        normalized-output-dir (-> base-output-dir
                                  (fs/file run-specific-subdir)
                                  fs/normalized)]
    (log/infof "Creating output dir for run: %s" (.getCanonicalPath
                                                  normalized-output-dir))
    (ks/mkdirs! normalized-output-dir)
    normalized-output-dir))

(schema/defn ^:always-validate mem-run!
  "Mainline function for the memcapture program.  Supplied with a
  decomposed config map from Trapperkeeper"
  [config :- {schema/Keyword schema/Any}
   _]
  (tk-config/initialize-logging! config)
  (let [jruby-puppet-config (jruby-puppet-core/initialize-config config)
        mem-measure-config (:mem-measure config)
        mem-output-run-dir (or (:output-dir mem-measure-config)
                               default-output-dir)
        mem-output-run-dir (create-output-run-dir! mem-output-run-dir)
        num-containers (or (:num-containers mem-measure-config)
                           default-num-containers)]
    (log/infof "Using %d containers for simulation" num-containers)
    (scenario/run-scenarios
     jruby-puppet-config
     mem-output-run-dir
     num-containers
     [{:name "create empty scripting containers"
       :fn (partial empty-scripting-containers/run-empty-scripting-containers-scenario
                    num-containers)}
      {:name "initialize puppet into scripting containers"
       :fn initialize-puppet-in-jruby-containers/run-initialize-puppet-in-jruby-containers-scenario}])))

(defn -main
  [& args]
  (cli/run mem-run! args))
