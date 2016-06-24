(ns puppetserver-memmeasure.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.scenarios.empty-scripting-containers
             :as empty-scripting-container-scenario]
            [puppetserver-memmeasure.scenarios.initialize-puppet-in-jruby-containers
             :as init-puppet-scenario]
            [puppetserver-memmeasure.scenarios.single-catalog-compile
             :as single-catalog-compile-scenario]
            [puppetlabs.puppetserver.cli.subcommand :as cli]
            [puppetlabs.services.jruby.jruby-puppet-core :as jruby-puppet-core]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.trapperkeeper.config :as tk-config]
            [clj-time.core :as clj-time-core]
            [clj-time.format :as clj-time-format]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [cheshire.core :as cheshire]
            [slingshot.slingshot :refer [try+]]
            [clojure.java.io :as io]
            [slingshot.slingshot :as sling])
  (:import (java.io File)
           (clojure.lang ExceptionInfo)))

(def default-output-dir "./target/mem-measure")
(def default-num-containers 4)
(def default-num-catalogs 4)

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
  [config :- {schema/Keyword schema/Any}]
  (tk-config/initialize-logging! config)
  (let [jruby-puppet-config (jruby-puppet-core/initialize-config config)
        mem-measure-config (:mem-measure config)
        mem-output-run-dir (create-output-run-dir!
                            (or (:output-dir mem-measure-config)
                                default-output-dir))
        num-containers (or (:num-containers mem-measure-config)
                           default-num-containers)
        num-catalogs (or (:num-catalogs mem-measure-config)
                         default-num-catalogs)
        result-file (fs/file mem-output-run-dir "results.json")]
    (log/infof "Using %d containers for simulation" num-containers)
    (-> jruby-puppet-config
        (scenario/run-scenarios
         mem-output-run-dir
         [{:name "create empty scripting containers"
           :fn (partial empty-scripting-container-scenario/run-empty-scripting-containers-scenario
                        num-containers)}
          {:name "initialize puppet into scripting containers"
           :fn init-puppet-scenario/run-initialize-puppet-in-jruby-containers-scenario}
          {:name "compile a single catalog with environment timeout unlimited"
           :fn (partial single-catalog-compile-scenario/run-single-catalog-compile-env-timeout-unlimited-scenario
                        num-catalogs)}
          {:name "compile a single catalog with environment timeout 0"
           :fn (partial single-catalog-compile-scenario/run-single-catalog-compile-env-timeout-zero-scenario
                        num-catalogs)}
          ])
        (assoc :num-containers num-containers)
        (assoc :num-catalogs num-catalogs)
        (cheshire/generate-stream (io/writer result-file)))
    (log/infof "Results written to: %s" (.getCanonicalPath result-file))))

(schema/defn mem-run-wrapper!
  "Wrapper for the mainline function for the memcapture program.  Basically
  just re-wraps any downstream Clojure ExceptionInfo which is thrown into
  a slingshot exception that the cli subcommand wrapper from Puppet Server
  should output properly, when applicable."
  [config :- {schema/Keyword schema/Any}
   _]
  (try
   (mem-run! config)
   (catch ExceptionInfo e
     (log/error e (.getMessage e))
     (sling/throw+
      {:type :cli-error
       :message (.getMessage e)}
      e))))

(defn -main
  [& args]
  (cli/run mem-run-wrapper! args))
