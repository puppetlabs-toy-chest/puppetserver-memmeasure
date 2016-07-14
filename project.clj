(def ps-version "2.4.0")

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
                 [puppetlabs/puppetserver ~ps-version]
                 [puppetlabs/trapperkeeper "1.4.0"]
                 [puppetlabs/kitchensink "1.3.0"]
                 [cheshire "5.6.1"]
                 [clj-time "0.11.0"]
                 [me.raynes/fs "1.4.6"]
                 [slingshot "0.12.2"]]
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
  :profiles {:uberjar {:aot :all}
             :jruby17 {:dependencies
                       [;; puppetserver defines hard dependencies on jffi
                        ;; and jnr-x86asm for JRuby 1.7 because, in JRuby's
                        ;; 1.7 poms, they are defined using version ranges.
                        ;; These transitive dependencies are excluded both
                        ;; from puppetserver and jruby-core and defined
                        ;; as explicit dependencies here because otherwise
                        ;; :pedantic? :abort won't tolerate this.
                        [puppetlabs/puppetserver ~ps-version
                         :exclusions [com.github.jnr/jffi
                                      com.github.jnr/jnr-x86asm]]
                        [org.jruby/jruby-core "1.7.20.1"
                         :exclusions [com.github.jnr/jffi
                                      com.github.jnr/jnr-x86asm]]
                        [org.jruby/jruby-stdlib "1.7.20.1"]
                        [com.github.jnr/jffi "1.2.9"]
                        [com.github.jnr/jffi "1.2.9" :classifier "native"]
                        [com.github.jnr/jnr-x86asm "1.0.2"]]}
             :jruby9k {:dependencies
                       ;; JRuby 9k's poms define explicit dependencies on jffi
                       ;; and jnr-x86asm (as opposed to version ranges, as
                       ;; JRuby 1.7's poms did).  Exclude the jffi and
                       ;; jnr-x86asm from puppetserver so that these can be
                       ;; picked up transitively from the explicit jruby 9k
                       ;; dependency instead.
                       [[puppetlabs/puppetserver ~ps-version
                         :exclusions [com.github.jnr/jffi
                                      com.github.jnr/jnr-x86asm]]
                        [org.jruby/jruby-core "9.1.2.0"]
                        [org.jruby/jruby-stdlib "9.1.2.0"]
                        [org.yaml/snakeyaml "1.14"]]}})
