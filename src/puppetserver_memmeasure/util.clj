(ns puppetserver-memmeasure.util
  (:require [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [puppetlabs.services.jruby.jruby-puppet-internal
             :as jruby-puppet-internal]
            [puppetlabs.services.jruby.puppet-environments :as puppet-env]
            [puppetlabs.services.jruby.jruby-puppet-internal :as jruby-internal]
            [puppetlabs.services.jruby.jruby-puppet-schemas :as jruby-schemas])
  (:import (com.yourkit.api Controller MemorySnapshot)
           (java.io File)
           (java.util HashMap)
           (com.puppetlabs.puppetserver JRubyPuppet)
           (com.puppetlabs.puppetserver.jruby ScriptingContainer)))

(schema/defn create-scripting-container
  :- ScriptingContainer
  [{:keys [ruby-load-path gem-home compile-mode]}
   :- jruby-schemas/JRubyPuppetConfig]
  (doto (jruby-puppet-internal/empty-scripting-container
         ruby-load-path
         gem-home
         compile-mode)
    (.runScriptlet "require 'jar-dependencies'")))

(schema/defn initialize-puppet-in-container
  :- JRubyPuppet
  [scripting-container :- ScriptingContainer
   {:keys [http-client-ssl-protocols http-client-cipher-suites
           http-client-connect-timeout-milliseconds
           http-client-idle-timeout-milliseconds
           use-legacy-auth-conf] :as config} :- jruby-schemas/JRubyPuppetConfig]
  (.runScriptlet scripting-container "require 'puppet/server/master'")
  (let [env-registry          (puppet-env/environment-registry)
        ruby-puppet-class     (.runScriptlet scripting-container "Puppet::Server::Master")
        puppet-config         (jruby-internal/config->puppet-config config)
        puppetserver-config  (HashMap.)]
    (when http-client-ssl-protocols
      (.put puppetserver-config "ssl_protocols" (into-array String http-client-ssl-protocols)))
    (when http-client-cipher-suites
      (.put puppetserver-config "cipher_suites" (into-array String http-client-cipher-suites)))
    (.put puppetserver-config "profiler" nil)
    (.put puppetserver-config "environment_registry" env-registry)
    (.put puppetserver-config "http_connect_timeout_milliseconds"
          http-client-connect-timeout-milliseconds)
    (.put puppetserver-config "http_idle_timeout_milliseconds"
          http-client-idle-timeout-milliseconds)
    (.put puppetserver-config "use_legacy_auth_conf" use-legacy-auth-conf)
    (.callMethodWithArgArray
     scripting-container
     ruby-puppet-class
     "new"
     (into-array Object
                 [puppet-config puppetserver-config])
     JRubyPuppet)))

(schema/defn take-yourkit-snapshot! :- schema/Int
  [snapshot-output-dir :- File
   snapshot-base-name :- schema/Str]
  (let [target-snapshot-file (fs/file snapshot-output-dir
                                      (str snapshot-base-name ".snapshot"))
        controller (doto (Controller.)
                     ;; Force GC twice to try to free up some
                     ;; WeakReferences before taking a snapshot.
                     (.forceGC)
                     (.forceGC))
        initial-snapshot-file (.captureMemorySnapshot controller)]
    (log/debugf "Snapshot captured to: %s" initial-snapshot-file)
    (fs/rename initial-snapshot-file target-snapshot-file)
    (log/infof "Snapshot renamed to: %s" (.getCanonicalPath target-snapshot-file))
    (-> target-snapshot-file
        (MemorySnapshot.)
        (.getShallowSize
         "<reachable-objects>
           <from>
             <roots/>
           </from>
           <object-filter>
             <not>
               <objects class=\"java.lang.ref.Finalizer\"/>
             </not>
           </object-filter>
           <field-filter>
             <class name=\"java.lang.ref.Reference\">
               <forbidden field=\"referent\"/>
             </class>
           </field-filter>
         </reachable-objects>"))))
