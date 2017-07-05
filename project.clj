(def yourkit-resources-dir "/Applications/YourKit-Java-Profiler-2016.02.app/Contents/Resources/")

(def yourkit-jar (str yourkit-resources-dir "lib/yjp.jar"))
(def yourkit-agent-lib (str yourkit-resources-dir
                            "bin/mac/libyjpagent.jnilib"))

(def yourkit-agentpath-jvm-opt (str "-agentpath:"
                                 yourkit-agent-lib
                                 "=disableall"))

(defproject puppetserver-memmeasure "0.1.0-SNAPSHOT"
  :description "Runs scenarios for measuring Puppet Server memory usage"
  :min-lein-version "2.7.1"
  :parent-project {:coords [puppetlabs/clj-parent "1.2.1"]
                   :inherit [:managed-dependencies]}
  :dependencies [[org.clojure/clojure]
                 [org.clojure/tools.logging]
                 [puppetlabs/puppetserver "5.0.0"]
                 [puppetlabs/jruby-utils "0.10.0"]
                 [puppetlabs/trapperkeeper]
                 [puppetlabs/kitchensink]
                 [cheshire]
                 [me.raynes/fs]]
  :main ^:skip-aot puppetserver-memmeasure.core
  :pedantic? :abort
  :repositories [["releases"
                  "http://nexus.delivery.puppetlabs.net/content/repositories/releases/"]
                 ["snapshots"
                  "http://nexus.delivery.puppetlabs.net/content/repositories/snapshots/"]]
  :plugins [[lein-parent "0.3.1"]]
  :target-path "target/%s"
  :jvm-opts [~yourkit-agentpath-jvm-opt]
  :aliases {"go" ["trampoline" "run" "--config" "./dev/puppetserver.conf"]
            "diff" ["trampoline" "run" "-m" "puppetserver-memmeasure.diff"]
            "summarize" ["trampoline"
                         "run"
                         "-m"
                         "puppetserver-memmeasure.summary"]}
  :resource-paths [~yourkit-jar]
  :profiles {:uberjar {:aot :all}
             :jruby9k {:dependencies [[puppetlabs/jruby-deps "9.1.11.0-1"]]}})
