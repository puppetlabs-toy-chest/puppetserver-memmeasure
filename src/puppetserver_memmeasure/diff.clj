(ns puppetserver-memmeasure.diff
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.kitchensink.core :as ks]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io]))

(def default-output-file "./compare.json")

(def cli-specs
  [["-b" "--base-summary-file BASE_SUMMARY_FILE" "Base summary file"
    :id :base-summary-file]
   ["-c" "--compare-summary-file COMPARE_SUMMARY_FILE" "Compare summary file"
    :id :compare-summary-file]
   ["-o" "--output-file OUTPUT_FILE" "Output file"
    :id :output-file
    :default default-output-file]])

(schema/defn diff-scenario :- memmeasure-schemas/DiffScenario
  [base-scenario :- memmeasure-schemas/SummarizedScenario
   compare-scenario :- memmeasure-schemas/SummarizedScenario]
  (let [compare-mean-mem-inc-after-first-step-over-base
        (- (:mean-mem-inc-after-first-step compare-scenario)
           (:mean-mem-inc-after-first-step base-scenario))

        compare-mean-mem-inc-after-second-step-over-base
        (- (:mean-mem-inc-after-second-step compare-scenario)
           (:mean-mem-inc-after-second-step base-scenario))

        compare-mem-inc-for-first-step
        (- (:mem-inc-for-first-step compare-scenario)
           (:mem-inc-for-first-step base-scenario))]
    {:compare-mean-mem-inc-after-first-step-over-base
     compare-mean-mem-inc-after-first-step-over-base
     :compare-mean-mem-inc-after-second-step-over-base
     compare-mean-mem-inc-after-second-step-over-base
     :compare-mem-inc-for-first-step
     compare-mem-inc-for-first-step
     :readable-compare-mean-mem-inc-after-first-step-over-base
     (util/human-readable-byte-count
      compare-mean-mem-inc-after-first-step-over-base)
     :readable-compare-mean-mem-inc-after-second-step-over-base
     (util/human-readable-byte-count
      compare-mean-mem-inc-after-second-step-over-base)
     :readable-mem-inc-for-first-step
     (util/human-readable-byte-count
      compare-mem-inc-for-first-step)
     :base base-scenario
     :compare compare-scenario}))

(schema/defn ^:always-validate diff-scenarios
  :- memmeasure-schemas/DiffScenarios
  [base-scenarios :- memmeasure-schemas/SummarizedScenarios
   compare-scenarios :- memmeasure-schemas/SummarizedScenarios]
  (reduce
   (fn [acc base-scenario]
     (if-let [compare-scenario (get compare-scenarios (key base-scenario))]
       (assoc acc (key base-scenario)
                  (diff-scenario (val base-scenario) compare-scenario))
       acc))
   {}
   base-scenarios))

(schema/defn ^:always-validate full-diff
  :- memmeasure-schemas/FullDiffScenarioNamespaces
  [base-summary-file :- schema/Str
   compare-summary-file :- schema/Str]
  (let [base-summary (util/json-file->map base-summary-file)
        compare-summary (util/json-file->map compare-summary-file)]
    (reduce
     (fn [acc base-scenario-namespace]
       (if-let [compare-scenarios (get compare-summary
                                       (key base-scenario-namespace))]
         (assoc-in acc
                   [:diff (key base-scenario-namespace)]
                   (diff-scenarios (val base-scenario-namespace)
                                    compare-scenarios))
         acc))
     {:base-file (fs/base-name base-summary-file ".json")
      :compare-file (fs/base-name compare-summary-file ".json")
      :diff {}}
     base-summary)))

(defn -main
  [& args]
  (let [{:keys [base-summary-file
                compare-summary-file
                output-file]} (first (ks/cli! args cli-specs))
        output-dir (fs/parent output-file)]
    (log/infof "Creating output dir for diff: %s"
               (.getCanonicalPath output-dir))
    (ks/mkdirs! output-dir)
    (cheshire/generate-stream
     (full-diff base-summary-file compare-summary-file)
     (io/writer output-file))
    (log/infof "Diff written to: %s" output-file)))
