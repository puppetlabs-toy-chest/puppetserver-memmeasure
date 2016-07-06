(ns puppetserver-memmeasure.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [puppetserver-memmeasure.scenario :as scenario]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetlabs.puppetserver.cli.subcommand :as cli]
            [puppetlabs.services.jruby.jruby-puppet-core :as jruby-puppet-core]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.trapperkeeper.config :as tk-config]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [cheshire.core :as cheshire]
            [slingshot.slingshot :refer [try+]]
            [clojure.java.io :as io]
            [slingshot.slingshot :as sling]
            [clojure.string :as str])
  (:import (clojure.lang ExceptionInfo)))

(def default-output-dir "./target/mem-measure")
(def default-node-names ["small"])
(def default-num-containers 4)
(def default-num-catalogs 4)
(def default-num-environments 4)
(def default-environment-name "production")
(def default-environment-timeout "unlimited")

(def cli-specs
  [["-c" "--num-catalogs NUM_CATALOGS" "Number of catalogs to use"
    :id :num-catalogs
    :default default-num-catalogs
    :parse-fn #(Integer/parseInt %)]
   ["-e" "--environment-name ENV_NAME" "Name of environment to use"
    :id :environment-name
    :default default-environment-name]
   ["-j" "--num-containers NUM_CONTAINERS" "Number of JRuby containers to use"
    :id :num-containers
    :default default-num-containers
    :parse-fn #(Integer/parseInt %)]
   ["-n" "--node-names NODE_NAMES"
    "Node name(s) to use - separated by commas for catalog requests"
    :id :node-names
    :default default-node-names
    :parse-fn #(str/split % #",")]
   ["-o" "--output-dir OUTPUT_DIR" "Output directory"
    :id :output-dir
    :default default-output-dir]
   ["-r" "--num-environments NUM_ENVIRONMENTS" "Number of environments to use"
    :id :num-environments
    :default default-num-environments
    :parse-fn #(Integer/parseInt %)]
   ["-s" "--scenario-ns SCENARIO_NS"
    "Namespace to run scenarios from"
    :id :scenario-ns
    :default "basic-scripting-containers"]
   ["-t" "--environment-timeout ENV_TIMEOUT"
    "Environment timeout to use - 0 or 'unlimited'"
    :id :environment-timeout
    :default default-environment-timeout
    :validate-fn #(schema/validate memmeasure-schemas/EnvironmentTimeout %)]])

(schema/defn ^:always-validate mem-run!
  "Mainline function for the memcapture program.  Supplied with a
  decomposed config map from Trapperkeeper and list of additional
  non-Trapperkeeper processed options supplied from the command-line, if any."
  [config :- {schema/Keyword schema/Any}
   options :- [schema/Str]]
  (tk-config/initialize-logging! config)
  (let [parsed-cli-options (first (ks/cli! options cli-specs))
        jruby-puppet-config (jruby-puppet-core/initialize-config config)
        scenario-ns (:scenario-ns parsed-cli-options)
        mem-output-run-dir (fs/file (:output-dir parsed-cli-options))
        result-file (fs/file mem-output-run-dir
                             (str
                              scenario-ns
                              "-"
                              "results.json"))
        scenario-ns-file (str "src/puppetserver_memmeasure/scenarios/"
                              (str/replace scenario-ns "-" "_")
                              ".clj")
        scenario-ns-symbol (symbol (str "puppetserver-memmeasure.scenarios."
                                        (:scenario-ns parsed-cli-options)
                                        "/scenario-data"))
        scenario-config (select-keys parsed-cli-options
                                     [:num-containers
                                      :num-catalogs
                                      :num-environments
                                      :environment-name
                                      :environment-timeout
                                      :node-names])]
    (log/infof "Loading scenario ns file: %s" scenario-ns-file)
    (load-file scenario-ns-file)
    (if-let [scenario-data (resolve scenario-ns-symbol)]
      (do
        (log/infof "Creating output dir for run: %s"
                   (.getCanonicalPath mem-output-run-dir))
        (ks/mkdirs! mem-output-run-dir)
        (log/info "Config for simulation: " scenario-config)
        (-> scenario-ns
            (scenario/run-scenarios
             scenario-config
             jruby-puppet-config
             mem-output-run-dir
             (scenario-data))
            (cheshire/generate-stream (io/writer result-file)))
        (log/infof "Results written to: %s" (.getCanonicalPath result-file)))
      (log/errorf "Unable to locate scenario data for: %s" scenario-ns-symbol))))

(schema/defn mem-run-wrapper!
  "Wrapper for the mainline function for the memcapture program.  Basically
  just re-wraps any downstream Clojure ExceptionInfo which is thrown into
  a slingshot exception that the cli subcommand wrapper from Puppet Server
  should output properly, when applicable."
  [config :- {schema/Keyword schema/Any}
   options :- [schema/Str]]
  (try
   (mem-run! config options)
   (catch ExceptionInfo e
     (let [exception-type (:type (.getData e))]
       (if (and exception-type
                (keyword? exception-type)
                (or (= (ks/without-ns exception-type) :cli-error)
                    (= (ks/without-ns exception-type) :cli-help)))
         (sling/throw+ e)
         (do
           ;; Non-cli error, recast to cli slingshot error
           (log/error e (.getMessage e))
           (sling/throw+
            {:type ::cli-error
             :message (.getMessage e)}
            e)))))))

(defn -main
  [& args]
  (cli/run mem-run-wrapper! args))
