(ns puppetserver-memmeasure.summary
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [puppetserver-memmeasure.schemas :as memmeasure-schemas]
            [puppetserver-memmeasure.util :as util]
            [puppetlabs.kitchensink.core :as ks]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [cheshire.core :as cheshire]
            [clojure.java.io :as io])
  (:import (java.io File)))

(def default-base-results-dir "./target/mem-measure")
(def default-output-file (-> default-base-results-dir
                             (fs/file "summary.json")
                             fs/normalized))

(def cli-specs
  [["-d" "--base-results-dir BASE_RESULTS_DIR" "Base results directory"
    :id :base-results-dir
    :default default-base-results-dir]
   ["-o" "--output-file OUTPUT_FILE" "Output file"
    :id :output-file
    :default default-output-file]])

(schema/defn ^:always-validate scenario-result-summary
  :- memmeasure-schemas/SummarizedScenarios
  [path :- File]
  (let [result (util/json-file->map path)]
    (reduce
     (fn [acc scenario]
       (let [{:keys [mean-mem-inc-after-first-step
                     mean-mem-inc-after-second-step
                     mem-inc-for-first-step]} (:results scenario)]
         (assoc acc
           (keyword (:name scenario))
           {:mean-mem-inc-after-first-step mean-mem-inc-after-first-step
            :readable-mean-mem-inc-after-first-step (util/human-readable-byte-count
                                                     mean-mem-inc-after-first-step)
            :mean-mem-inc-after-second-step mean-mem-inc-after-second-step
            :readable-mean-mem-inc-after-second-step (util/human-readable-byte-count
                                                      mean-mem-inc-after-second-step)
            :mem-inc-for-first-step mem-inc-for-first-step
            :readable-mem-inc-for-first-step (util/human-readable-byte-count
                                              mem-inc-for-first-step)
            :config (:config scenario)})))
     {}
     (:scenarios result))))

(schema/defn ^:always-validate full-summary
  :- memmeasure-schemas/SummarizedScenarioNamespaces
  [base-results-dir :- schema/Str]
  (reduce
   (fn [acc path]
     (assoc acc
       (-> path fs/parent fs/name keyword)
       (scenario-result-summary path)))
   {}
   (fs/find-files base-results-dir #".*-results.json$")))

(defn -main
  [& args]
  (let [{:keys [base-results-dir output-file]} (first (ks/cli! args cli-specs))
        output-dir (fs/parent output-file)]
    (log/infof "Creating output dir for summary: %s"
               (.getCanonicalPath output-dir))
    (ks/mkdirs! output-dir)
    (cheshire/generate-stream
     (full-summary base-results-dir)
     (io/writer output-file))
    (log/infof "Summary written to: %s" output-file)))
