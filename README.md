# puppetserver-memmeasure

Tool for measuring memory usage related to JRuby and
[Puppet Server](https://github.com/puppetlabs/puppetserver).

This project expresses a hard dependency on an upstream
"puppetlabs/puppetserver" artifact, basically using "puppetserver" as a helper
library for tasks like creating JRuby ScriptingContainers, configuring Ruby
Puppet within those containers, and compiling Puppet code catalogs via the
containers.

## Installation

### Initialize Project Submodules

Similar to the upstream Puppet Server project, this project uses git
submodules under the "./ruby" directory:

* puppet
* facter
* hiera

To ensure that the submodules are properly initialized during the initial
clone of the project, include the "--recursive" argument when performing the
initial clone.

If you have already cloned the project without supplying the "--recursive"
argument, you should be able to initialize the submodules by running:

    $ git submodule init
    
From time to time, the submodules may be updated to point to newer versions.
To ensure you are using the latest submodule commits at any time later on, run:

    $ git submodule update

### Install YourKit Java Profiler and Configure Library Paths

This tool relies upon some libraries in the
[YourKit Java Profiler](https://www.yourkit.com/) for gathering memory
statistics.  These libraries are distributed with a YourKit installation, not
via one of the public jar repositories (like Clojars and Maven Central) which
most public Clojure projects reference.  Before being able to run this tool, the
full YourKit Java Profiler package must be installed.  Note the the use of
these libraries would require a YourKit license.

The tool depends upon two YourKit libraries:

* yjp.jar - The YourKit Jar has APIs that the code calls into for taking
  snapshots of the currently running process and analyzing the amount of
  memory being used.  

* libyjpagent shared library - In order for the YourKit APIs to be functional,
  the process in which the APIs are invoked must have been started with
  the libyjpagent shared library attached.  The exact name of this library
  differs from platform to platform.  See
  https://www.yourkit.com/docs/java/help/agent.jsp for more information.

In order to be able to run the tool locally via Leiningen, you will first
probably need to make some adjustments to the "yourkit-" definitions in the
"project.clj" file.  The default values for these variables in the "project.clj"
file reflect a "2016.02" YourKit installation on a Mac.

~~~clj
(def yourkit-resources-dir "/Applications/YourKit-Java-Profiler-2016.02.app/Contents/Resources/")

(def yourkit-jar (str yourkit-resources-dir "lib/yjp.jar"))
(def yourkit-agent-lib (str yourkit-resources-dir
                            "bin/mac/libyjpagent.jnilib"))
~~~                            

### Ensure Access to the "puppetlabs/puppetserver" jar

The 'puppetlabs/puppetserver' jar that this project is dependent upon is not
published to a public Clojure repository.  To resolve this dependency, either
the puppetserver jar could be built / installed to a local Maven repository
from source in the GitHub
[puppetserver](https://github.com/puppetlabs/puppetserver) project and/or
downloaded from the internal nexus.delivery.puppetlabs.net server.

### Install the r10k gem

Various scenarios written for the tool require an environment from an
[r10k](https://github.com/puppetlabs/r10k) control repo to be installed.  The
specific base branch that the tool uses is
"20160622_SERVER_1390-catalog-memory-measurement" in the 
[puppetlabs-puppetserver_perf_control](https://github.com/puppetlabs/puppetlabs-puppetserver_perf_control/tree/20160622_SERVER-1390-catalog-memory-measurement) repo.

In order to be able to use `r10k` to deploy the control repo, the `r10k` ruby
gem should first be installed:

    $ gem install r10k

## Usage

To run all scenarios, execute the following from the command line:

    $ ./dev/run.sh

The script performs two steps:

1. Uses the `r10k` gem to deploy an environment from the
   `puppetlabsl-puppetserver_perf_control` repo.
   
2. Via [Leiningen](http://leiningen.org), runs various memory scenarios against
   the Puppet code deployed from the control repo.

---

The tool can also be run via Leiningen directly the command line.  The following
command would run the tool with default arguments, exercising only a default
memory scenario, `basic-scripting-container`:

    $ lein go

"go" is an alias which expands to: 
    
    $ lein trampoline run --config ./dev/puppetserver.conf 
 
The memory measurement tool uses
[Trapperkeeper](https://github.com/puppetlabs/trapperkeeper) to load
configuration settings which pertain to Puppet Server, `./dev/puppetserver.conf`
for the "go" alias.  You may in some cases want to customize this default
configuration file.  To do that, you could copy the file to another location and
run the tool with the custom location of the config file.  For example:

     $ cp ./dev/puppetserver.conf ~/.puppetserver.conf
     $ lein run --config ~/.puppetserver.conf

Assuming `lein uberjar` has been used to build an uberjar of the tool + all of
its upstream dependencies related to Puppet Server, the tool could be run from
a Java command line like the following:
 
    $ java -cp /Applications/YourKit-Java-Profiler-2016.02.app/Contents/Resources/lib/yjp.jar:./target/uberjar/puppetserver-memmeasure-0.1.0-SNAPSHOT-standalone.jar \
        -agentpath:/Applications/YourKit-Java-Profiler-2016.02.app/Contents/Resources/bin/mac/libyjpagent.jnilib=disableall \
        clojure.main -m puppetserver-memmeasure.core --config ~/puppetserver.conf

Note that the paths to the "yjp.jar" and "libyjpagent" files from YourKit would
need to be adjusted to reflect the appropriate local installation directory.

While the tool is running, various output files - including memory snapshots
taken during individual scenario steps and a "results.json" file with the final
test results - are written into an output directory.  The name of the
output directory can be controlled via configuration - see the
[Options](#options) section for more details.  Log messages indicate the
fully-qualified paths to each of the files that the tool writes.  Some examples
include:

~~~
2016-06-20 17:35:18,433 INFO  [main] [p.core] Creating output dir for run: .../target/mem-measure
2016-06-20 17:35:34,325 INFO  [main] [p.util] Snapshot renamed to: .../target/mem-measure/create-container-0.snapshot
2016-06-20 17:37:53,325 INFO  [main] [p.scenario] Results written to: .../target/mem-measure/results.json
~~~

## Output

The "results" file created at the end of the run has a roll-up of all of the
steps performed for various scenarios and statistics about memory that was
measured during the simulation.

Here is a subset of the output - pretty-print formatted via the use of Python's
JSON tool (`python -m json-tool <json file>`) - from one example run:

~~~json
{
    "config": {
        "environment-timeout": "0",
        "node-name": "small",
        "num-catalogs": 10,
        "num-containers": 4
    },
    "mem-inc-for-all-scenarios": 88845648,
    "mem-used-after-last-scenario": 119728136,
    "mem-used-before-first-scenario": 30882488,
    "num-containers": 4
    "num-catalogs": 4
    "scenarios": [
        {
            "name": "create empty scripting containers",
            "results": {
                "mean-mem-inc-after-first-step": 5019624,
                "mean-mem-inc-after-second-step": 5006880,
                "mem-at-scenario-end": 59217712,
                "mem-at-scenario-start": 32472376,
                "mem-inc-for-first-step": 11686464,
                "mem-inc-for-scenario": 26745336,
                "std-dev-mem-inc-after-first-step": 18102.578232579654,
                "std-dev-mem-inc-after-second-step": 2080.0,
                "steps": [
                    {
                        "mem-inc-over-previous-step": 16321000,
                        "mem-used-after-step": 30673968,
                        "name": "create-container-0"
                    },
                    {
                        "mem-inc-over-previous-step": 5185600,
                        "mem-used-after-step": 35859568,
                        "name": "create-container-1"
                    },
                    ...
                ]
            }
        },
        {
            "name": "initialize puppet into scripting containers",
            "results": {
                "mean-mem-inc-after-first-step": 31394736,
                "mean-mem-inc-after-second-step": 31399468,
                "mem-at-scenario-end": 191836712,
                "mem-at-scenario-start": 59246192,
                "mem-inc-for-first-step": 38406312,
                "mem-inc-for-scenario": 132590520,
                "std-dev-mem-inc-after-first-step": 6934.909564418751,
                "std-dev-mem-inc-after-second-step": 2228.0,
                "steps": [
                    {
                        "mem-inc-over-previous-step": 38582264,
                        "mem-used-after-step": 84494400,
                        "name": "initialize-puppet-in-container-0"
                    },
                    {
                        "mem-inc-over-previous-step": 31515416,
                        "mem-used-after-step": 116009816,
                        "name": "initialize-puppet-in-container-1"
                    },
                ...
                ]
            }
        }
        ...
    ]
}       
~~~

Each of the numbers in the "mem-" measurements is expressed in bytes.  For
example, the value 14352968 in `mem-used-before-first-scenario` is about
13.69 MB.  The byte count represents the cumulative number of bytes allocated
in the JVM heap for objects which are reachable from Garbage Collection (GC)
roots via at least one strong reference.  Objects only accessible from weak,
soft, or phantom references and/or that are queued for finalization are ignored
under the assumption that these should eventually be freed up during a GC cycle.
The number computed should be very close to what the YourKit Java Profiler GUI
would show as the cumulative "shallow size" for all "strong reachable" objects.
See https://www.yourkit.com/docs/java/help/reachability.jsp for more information
on object reachability scope and YourKit's related interpretation.

The `mem-used-before-first-scenario` and `mem-used-after-last-scenario`
numbers represent memory allocated before vs. after all scenarios are run,
respectively.  `mem-inc-for-all-scenarios` is the difference between the two.

For each memory scenario run, a map appears under the "scenarios" key.  The
scenarios appear in the exact order in which the tool executes them.  The
following keys appear in the "results" map for each scenario.

* `mem-inc-for-first-step` - Increase in memory allocated after the first step
  in the scenario was run vs. before the first step was run.
  
* `mean-mem-inc-after-first-step` - Mean increase in memory before/after each
  step run after the first one (i.e., second to last).  The memory measurement
  for the first step is ignored for this value - and the corresponding
  `std-dev-mem-inc-per-additional-step` value - under the assumption that memory
  usage may expectedly increase significantly the first time a repeated action
  is performed vs. subsequent times that the action is performed.
  
* `mean-mem-inc-after-second-step` - Basically the same as
  `mean-mem-inc-after-first-step` except that the memory measurements used are
  the ones from third to last instead of second to last.
  
* `std-dev-mem-inc-after-first-step` - Standard deviation in memory allocated
  before/after each step beyond the first one was run.  The 'mean' used as the
  base for computing the standard deviation is the same as the
  `mean-mem-inc-after-first-step` value.

* `std-dev-mem-inc-after-second-step` - Basically the same as
  `std-dev-mem-inc-after-first-step` except that the memory measurements used
  are the ones from third to last instead of second to last.
  
Each of the "steps" performed in a memory scenario, listed in the json file in
the exact order in which they are executed, includes the following:
 
* `mem-inc-over-previous-step` - Increase in memory allocated after the step is
  run as compared to the memory allocated before the step was run.
  
* `mem-used-after-step` - Total memory allocated after the step is run.

For each value which expresses the "increase" in memory allocated between steps,
a positive number would represent an increase whereas a negative number would
represent a decrease.

## Options

Command line options can be specified in two segments, delimited by "--":
 
    $ lein run <first segment> -- <second segment>
 
The only available option recognized for the first segment is
"--config <config file/directory name>", which is used to select the
configuration directory or file name with settings pertaining to
[Puppet Server](https://github.com/puppetlabs/puppetserver/blob/master/documentation/config_file_puppetserver.markdown)
and its related [Trapperkeeper-based](https://github.com/puppetlabs/trapperkeeper)
service dependencies:

    $ lein run --config dev/puppetserver.conf

Relevant Puppet Server / Trapperkeeper configuration sections/settings include:

* global.logging-config
* jruby-puppet
* http-client

Other options which are memory-measurement tool specific can be specified in
the second segment of the command line.  For example, the output directory
for the application can be specified via the `-o` option:

    $ lein run --config dev/puppetserver.conf -- -o ./some-directory

This section also includes a "mem-measure" section with options specific to the
memory measurement tool.  Options in this section include:

* `-c | --numcatalogs NUM_CATALOGS` - For a relevant scenario, specifies the
  number of catalog compilations that the scenario should perform per
  environment within a jruby instance.  If not specified and the scenario
  requests catalogs, `4` catalogs will be requested.

* `-e | --environment-timeout ENV_TIMEOUT` - Prior to running the scenario,
  configure a 'puppet.conf' file in the directory referenced by the
  `jruby-puppet.master-conf-dir` setting from the Puppet Server / Trapperkeeper
  configuration file.  If not specified, the `environment_timeout` will be
  set to `unlimited`.

* `-j | --num-containers NUM_CONTAINERS` - For a relevant scenario, specifies
  the number of JRuby ScriptingContainers that the scenario should create.  If
  not specified and the scenario creates containers, `4` containers will be
  created.
  
* `-n | --node-name NODE_NAME` - For a relevant scenario, specifies the node
  name that the scenario should use (e.g., when requesting a catalog for the
  node).  If not specified and the scenario uses a node name, `small` will be
  used as the default.

* `-o | --output-dir OUTPUT_DIR` - The directory under which the output (memory
  snapshots, etc.) of the tool will be written.  This setting is optional.  If
  it is not specified, output will be written under a base directory in the
  repo clone called "./target/mem-measure".
  
* `-s | --scenario-ns SCENARIO_NS` - Namespace of the scenario that should be
  executed.  Relates to the subpath of the scenario implementation's Clojure
  namespace.  To execute the scenario in the Clojure
  `:puppetserver-memmeasure.scenarios.single-catalog-compile` namespace,
  "single-catalog-compile" would need to be specified.  If not specified,
  the "basic-scripting-container" scenario is run by default.
  
  
