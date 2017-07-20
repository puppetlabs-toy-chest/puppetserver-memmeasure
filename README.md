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
   `puppetlabs-puppetserver_perf_control` repo.
   
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
[Options](#options) section for more details.  The "results.json" file's name
will be prefixed with the base name of the output directory.  For example, if
the output directory were "/home/user/my-test-output", the corresponding
results file would be written to
"/home/user/my-test-output/my-test-output-results.json".
    
Log messages indicate the fully-qualified paths to each of the files that the
tool writes.  Some examples include:

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
        "nodes": [
          {
            "expected-class-in-catalog": "role::by_size::small",
            "name": "empty"
          }
        ],
        "num-catalogs": 10,
        "num-containers": 4
    },
    "mem-inc-for-all-scenarios": 88845648,
    "mem-used-after-last-scenario": 119728136,
    "mem-used-before-first-scenario": 30882488,
    "num-containers": 4,
    "num-catalogs": 4,
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

* `-e | --environment-name ENV_NAME` - For a relevant scenario, specifies the
  base environment name that the scenario should use.  If not specified,
  `production` will be used as the base environment name.

* `-j | --num-containers NUM_CONTAINERS` - For a relevant scenario, specifies
  the number of JRuby ScriptingContainers that the scenario should create.  If
  not specified and the scenario creates containers, `4` containers will be
  created.
  
* `-n | --nodes NODES` - For a relevant scenario, specifies one or more pairs,
  delimited by semicolons, where each pair contains a node name and, optionally,
  a comma and name of a class that is expected appear in the response for a
  catalog requested for the node.
  
  Examples:
  
  - `small` - Use the node name `small`.  If the scenario compiles catalogs,
    catalogs will be compiled for the `small` node.
  - `small,role::by_size::small` - Use the node name `small`.  For any
    catalogs requested for the node, validate that the response includes a
    class named `role::by_size::small`.  If the response does not contain
    the class, an exception is thrown and the scenario is terminated.
  - `small;empty` - Use the node names of `small` and `empty`.  If the scenario
    compiles catalogs, catalogs will be compiled for both the `small` and
    `empty` node.
  - `small,role::by_size::small;empty,role::by_size_empty` - Use the node
    names of `small` and `empty`.  If a catalog is compiled for the `small`
    node and the response does not include a class named `role::by_size::small`,
    an exception is thrown and the scenario is terminated.  If a catalog is
    compiled for the `empty` node and the response does not include a class
    named `role::by_size::empty`, an exception is thrown and the scenario is
    terminated.
    
  If not specified and the scenario uses a node name,
  `small,role::by_size::small` will be used as the default.

* `-o | --output-dir OUTPUT_DIR` - The directory under which the output (memory
  snapshots, etc.) of the tool will be written.  This setting is optional.  If
  it is not specified, output will be written under a base directory in the
  repo clone called "./target/mem-measure".

* `-r | --num-environments NUM_ENVIRONMENTS` - For a relevant scenario,
  specifies the number of environments that the scenario should create /
  compile catalogs within.  If not specified, `4` environments will be used.
 
* `-s | --scenario-ns SCENARIO_NS` - Namespace of the scenario that should be
  executed.  Relates to the subpath of the scenario implementation's Clojure
  namespace.  To execute the scenario in the Clojure
  `:puppetserver-memmeasure.scenarios.single-catalog-compile` namespace,
  "single-catalog-compile" would need to be specified.  If not specified,
  the "basic-scripting-container" scenario is run by default.
  
* `-t | --environment-timeout ENV_TIMEOUT` - Prior to running the scenario,
  configure a 'puppet.conf' file in the directory referenced by the
  `jruby-puppet.master-conf-dir` setting from the Puppet Server / Trapperkeeper
  configuration file.  If not specified, the `environment_timeout` will be
  set to `unlimited`.  

## Summarize

To combine the json output from multiple individual scenario json files
together, the `lein summarize` alias can be used.  The json output from a
`lein run` for an individual scenario contains memory measurement info after
each step performed.  For brevity, `lein summarize` only captures result (and
not also the per-step) info for each of the scenarios.

Example:
 
    $ lein summarize -d results -o results-summary.json

Options include:

* `-d | --base-results-dir BASE_RESULTS_DIR` - The directory under which json
  results files to be summarized reside.  File names under this directory which
  end with `-results.json` are assumed to have scenario results to be summarized.

* `-o | --output-file OUTPUT_FILE` - Output json file into which the summarized
  scenario information is written.

Here is a subset of the output - pretty-print formatted via the use of Python's
JSON tool (`python -m json-tool <json file>`) - from one example run:

~~~json
{
    "basic-scripting-containers": {
        "create empty scripting containers": {
            "config": {
                "num-containers": 5
            },
            "mean-mem-inc-after-first-step": 5017184,
            "mean-mem-inc-after-second-step": 5006618.666666667,
            "mem-inc-for-first-step": 11530456,
            "readable-mean-mem-inc-after-first-step": "5 MiB",
            "readable-mean-mem-inc-after-second-step": "5 MiB",
            "readable-mem-inc-for-first-step": "11 MiB"
        },
        "initialize puppet into scripting containers": {
            "config": {
                "num-containers": 5
            },
            ...
        }
    },
    "catalog-medium-group-by-catalog-timeout-0": {
        "compile catalogs in one jruby and environment, grouping by catalog": {
            "config": {
                "environment-name": "20160808_SERVER_1448_catalog_memory_measurement_with_hiera",
                "environment-timeout": "0",
                "nodes": [
                    {
                        "expected-class-in-catalog": "role::by_size::medium",
                        "name": "medium"
                    }
                ],
                ...
            },
            ...
        },
    },
    ...
}
~~~

The summarize output provides keys prefixed by `readable-` where the
corresponding memory measurement is rounded to a more human-readable value than
just the raw byte count.  The `readable-` values are expressed either in
mebibytes (MiB) or kibibytes (KiB), where 1 MiB = 220 bytes = 1,048,576 bytes.
The following rounding is applied for specific values:

* Values above 999 KiB are rounded up to the nearest MiB.

* Values above 800 KiB are rounded up to 1 MiB.

* Values above 200 KiB are rounded up to the nearest 100th KiB.

* Values below 200 KiB are rounded up to the nearest KiB.

## Diff

To compare the numbers from two summary result files, the `lein diff` alias can
be used.

Example:

    $ lein diff -b base.json -c compare.json -o diff.json

Options include:

* `-b | --base-summary-file BASE_SUMMARY_FILE` - The base summary file to use
  in the comparison.

* `-c | --compare-summary-file COMPARE_SUMMARY_FILE` - The summary file to
  compare to the base file supplied with the `-b` option.

* `-o | --output-file OUTPUT_FILE` - The file to write the diff output into.

Here is a subset of the output - pretty-print formatted via the use of Python's
JSON tool (`python -m json-tool <json file>`) - from one example run:

~~~json
{
    "base-file": "jruby17-compile-mode-off-summary",
    "compare-file": "jruby9k-compile-mode-off-summary",
    "diff": {
        "basic-scripting-containers": {
            "create empty scripting containers": {
                "base": {
                    "config": {
                        "num-containers": 5
                    },
                    "mean-mem-inc-after-first-step": 5015324,
                    "mean-mem-inc-after-second-step": 5008418.666666667,
                    "mem-inc-for-first-step": 11706920,
                    "readable-mean-mem-inc-after-first-step": "5 MiB",
                    "readable-mean-mem-inc-after-second-step": "5 MiB",
                    "readable-mem-inc-for-first-step": "12 MiB"
                },
                "compare": {
                    "config": {
                        "num-containers": 5
                    },
                    "mean-mem-inc-after-first-step": 6855322,
                    "mean-mem-inc-after-second-step": 6849272,
                    "mem-inc-for-first-step": 17990696,
                    "readable-mean-mem-inc-after-first-step": "7 MiB",
                    "readable-mean-mem-inc-after-second-step": "7 MiB",
                    "readable-mem-inc-for-first-step": "18 MiB"
                },
                "compare-mean-mem-inc-after-first-step-over-base": 1839998,
                "compare-mean-mem-inc-after-second-step-over-base": 1840853.333333333,
                "compare-mem-inc-for-first-step": 6283776,
                "readable-compare-mean-mem-inc-after-first-step-over-base": "2 MiB",
                "readable-compare-mean-mem-inc-after-second-step-over-base": "2 MiB",
                "readable-mem-inc-for-first-step": "6 MiB"
            },
            "initialize puppet into scripting containers": {
            ...
            },
        },
        "catalog-empty-group-by-catalog-timeout-0": {
           ...
        },
        ...
    },
}
~~~
