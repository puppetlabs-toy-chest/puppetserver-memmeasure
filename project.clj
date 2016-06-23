(def yourkit-resources-dir "/Applications/YourKit-Java-Profiler-2016.02.app/Contents/Resources/")

(def yourkit-jar (str yourkit-resources-dir "lib/yjp.jar"))
(def yourkit-agent-lib (str yourkit-resources-dir
                            "bin/mac/libyjpagent.jnilib"))

(def yourkit-agentpath-jvm-opt (str "-agentpath:"
                                 yourkit-agent-lib
                                 "=disableall"))

(defproject puppetserver-memmeasure "0.1.0-SNAPSHOT"
  :description "Runs scenarios for measuring Puppet Server memory usage"
  :dependencies [[org.clojure/clojure "1.7.0"]

                 ;; begin version conflict resolution dependencies
                 [org.clojure/tools.cli "0.3.4"]
                 ;; end version conflict resolution dependencies

                 [org.clojure/tools.logging "0.3.1"]
                 [puppetlabs/puppetserver "2.4.0"]
                 [puppetlabs/trapperkeeper "1.4.0"]
                 [puppetlabs/kitchensink "1.3.0"]
                 [cheshire "5.6.1"]
                 [clj-time "0.11.0"]
                 [me.raynes/fs "1.4.6"]]
  :main ^:skip-aot puppetserver-memmeasure.core
  :pedantic? :abort
  :repositories [["releases"
                  "http://nexus.delivery.puppetlabs.net/content/repositories/releases/"]
                 ["snapshots"
                  "http://nexus.delivery.puppetlabs.net/content/repositories/snapshots/"]]
  :target-path "target/%s"
  :jvm-opts [~yourkit-agentpath-jvm-opt]
  :aliases {"go" ["trampoline" "run" "--config" "./dev/puppetserver.conf"]}
  :resource-paths [~yourkit-jar]
  :profiles {:uberjar {:aot :all}})
