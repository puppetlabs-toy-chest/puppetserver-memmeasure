#!/bin/bash

base_output_dir=./target/mem-measure/`date +"%Y%m%d-%H%M%S"`
environment_timeout=""
num_catalogs=""
num_containers=""

while getopts ":c:j:o:" opt; do
  case $opt in
     c)
       num_catalogs="-c $OPTARG ";;
     j)
       num_containers="-j $OPTARG ";;
     o)
       base_output_dir="$OPTARG";;
     \?)
       echo "Invalid option: -$OPTARG" >&2
       exit 1;;
     :)
       echo "Option -$OPTARG requires an argument." >&2
       exit 1;;
   esac
 done

run_cmd="lein go -- ${num_catalogs}${num_containers}-o ${base_output_dir}/"
echo "run is $run_cmd"

echo "deploying r10k environment..."
set -x
r10k deploy environment 20160622-SERVER-1390-catalog-memory-measurement -p -v debug -c ./dev/r10k.yaml
set +x

echo "running scenarios, outputting to: $base_output_dir..."
set -x
${run_cmd}basic-scripting-containers -s basic-scripting-containers
${run_cmd}single-catalog-compile-empty-env-timeout-0 -e 0 -n empty -s single-catalog-compile
${run_cmd}single-catalog-compile-empty-env-timeout-unlimited -e unlimited -n empty -s single-catalog-compile
${run_cmd}single-catalog-compile-small-env-timeout-0 -e 0 -n small -s single-catalog-compile
${run_cmd}single-catalog-compile-small-env-timeout-unlimited -e unlimited -n small -s single-catalog-compile
set +x
