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

## Usage

The tool can be run from the command line via [Leiningen](http://leiningen.org)
with the following command line:

    $ lein go

"go" is an alias which expands to: 
    
    $ lein trampoline run --config ./dev/puppetserver.conf 
  
The memory measurement tool uses
[Trapperkeeper](https://github.com/puppetlabs/trapperkeeper) to load its
configuration, `./dev/puppetserver.conf` for the "go" alias.  You may in some
cases want to customize this default configuration file.  To do that, you could
copy the file to another location and run the tool with the custom location
of the config file.  For example:

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
test results - are written into an output directory.  The base name of the
output directory can be controlled via configuration - see the
[Options](#options) section for more details.  A run-specific subdirectory will
be created under the base output directory.  Log messages indicate the
fully-qualified paths to each of the files that the tool writes.  Some examples
include:

~~~
2016-06-20 17:35:18,433 INFO  [main] [p.core] Creating output dir for run: .../target/mem-measure/20160621T003518.427Z
2016-06-20 17:35:34,325 INFO  [main] [p.util] Snapshot renamed to: .../target/mem-measure/20160621T003518.427Z/create-container-0.snapshot
2016-06-20 17:37:53,325 INFO  [main] [p.scenario] Results written to: .../target/mem-measure/20160621T003518.427Z/results.json
~~~

## Output

The "results" file created at the end of the run has a roll-up of all of the
steps performed for various scenarios and statistics about memory that was
measured during the simulation.

Here is a subset of the output - pretty-print formatted via the use of Python's
JSON tool (`python -m json-tool <json file>`) - from one example run:

~~~json
{
    "mem-used-after-last-scenario": 178833600,
    "mem-used-before-first-scenario": 14352968,
    "num-containers": 4
    "scenarios": [
        {
            "name": "create empty scripting containers",
            "results": {
                "mean-mem-inc-per-additional-step": 5079389.333333333,
                "mem-inc-for-first-step": 16321000,
                "std-dev-mem-inc-per-additional-step": 75399.83952827015,
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
                "mean-mem-inc-per-additional-step": 31446400,
                "mem-inc-for-first-step": 38582264,
                "std-dev-mem-inc-per-additional-step": 53254.31973715059,
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
respectively.

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

The only command line option specific to this application is
"--config <config file/directory name>", which is used to select the
configuration directory or file name that the application uses.  Most other
configuration settings are derived from [Puppet Server](https://github.com/puppetlabs/puppetserver/blob/master/documentation/config_file_puppetserver.markdown).
Relevant configuration sections/settings include:

* global.logging-config
* jruby-puppet
* http-client

This section also includes a "mem-measure" section with options specific to the
memory measurement tool.  Options in this section include:

* `output-dir` - The base directory under which the output (memory snapshots,
  etc.) of the tool will be written.  This setting is optional.  If it is not
  specified, output will be written under a base directory in the repo clone
  called "./target/mem-measure".
  
* `num-containers` - The number of JRuby ScriptingContainers that the tool will
  create while running scenarios.  This setting is optional.  If it is not
  specified, 4 containers will be created.
