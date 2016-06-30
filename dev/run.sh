#!/bin/bash

set -e

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
${run_cmd}catalog-empty-one-jruby-one-environment-timeout-0 -e 0 -n empty -s catalog-one-node-one-jruby-one-environment
${run_cmd}catalog-empty-one-jruby-one-environment-timeout-unlimited -e unlimited -n empty -s catalog-one-node-one-jruby-one-environment
${run_cmd}catalog-small-one-jruby-one-environment-timeout-0 -e 0 -n small -s catalog-one-node-one-jruby-one-environment
${run_cmd}catalog-small-one-jruby-one-environment-timeout-unlimited -e unlimited -n small -s catalog-one-node-one-jruby-one-environment
${run_cmd}catalog-multiple-nodes-one-jruby-one-environment-timeout-0 -e 0 -s catalog-multiple-nodes-one-jruby-one-environment
${run_cmd}catalog-multiple-nodes-one-jruby-one-environment-timeout-unlimited -e unlimited -s catalog-multiple-nodes-one-jruby-one-environment
${run_cmd}catalog-small-multiple-jrubies-one-environment-timeout-0 -e 0 -n small -s catalog-one-node-multiple-jrubies-one-environment
${run_cmd}catalog-small-multiple-jrubies-one-environment-timeout-unlimited -e unlimited -n small -s catalog-one-node-multiple-jrubies-one-environment

set +x
