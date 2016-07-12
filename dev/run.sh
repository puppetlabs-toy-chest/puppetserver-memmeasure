#!/bin/bash

set -e

base_output_dir=./target/mem-measure/`date +"%Y%m%d-%H%M%S"`
deploy="y"
environment_name="20160622_SERVER_1390_catalog_memory_measurement"
lein_run_cmd="go"
num_catalogs="5"
num_containers="5"
num_environments="5"

default_node_names=(empty small)
nodes=
for node_name in ${default_node_names[@]}
do
  nodes="${nodes}${node_name},role::by_size::${node_name};"
done

while getopts ":c:e:f:ij:n:o:r:" opt; do
  case $opt in
     c)
       num_catalogs="$OPTARG";;
     e)
       environment_name="$OPTARG";;
     f)
       lein_run_cmd="trampoline run --config $OPTARG";;
     i)
       deploy="n";;
     j)
       num_containers="$OPTARG";;
     n)
       nodes="$OPTARG";;
     o)
       base_output_dir="$OPTARG";;
     r)
       num_environments="$OPTARG";;
     \?)
       echo "Invalid option: -$OPTARG" >&2
       exit 1;;
     :)
       echo "Option -$OPTARG requires an argument." >&2
       exit 1;;
   esac
 done

run_cmd="lein ${lein_run_cmd} -- -e ${environment_name} -o ${base_output_dir}/"

run_catalog_scenarios_for_node()
{
  node_name=$1
  node_class=$2
  node_name_and_class=$1,$2

  set -x

  # run one-jruby-one-environment
  ${run_cmd}catalog-${node_name}-one-jruby-one-environment-timeout-0 \
    -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j 1 -r 1 \
    -s catalog-one-jruby-one-environment
  ${run_cmd}catalog-${node_name}-one-jruby-one-environment-timeout-unlimited \
    -c ${num_catalogs} -t unlimited -n ${node_name_and_class} -j 1 -r 1 \
    -s catalog-one-jruby-one-environment

  # run group-by-jruby with single environment
  ${run_cmd}catalog-${node_name}-group-by-jruby-1-env-timeout-0 \
    -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j ${num_containers} \
    -r 1 -s catalog-group-by-jruby
  ${run_cmd}catalog-${node_name}-group-by-jruby-1-env-timeout-unlimited \
    -c ${num_catalogs} -t unlimited -n ${node_name_and_class} \
    -j ${num_containers} -r 1 -s catalog-group-by-jruby

  if [ "$num_environments" != "1" ]; then
    # run group-by-jruby with configured number of environments
    ${run_cmd}catalog-${node_name}-group-by-jruby-${num_environments}-envs-timeout-0 \
      -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j ${num_containers} \
      -r ${num_environments} -s catalog-group-by-jruby
    ${run_cmd}catalog-${node_name}-group-by-jruby-${num_environments}-envs-timeout-unlimited \
      -c ${num_catalogs} -t unlimited -n ${node_name_and_class} \
      -j ${num_containers} -r ${num_environments} -s catalog-group-by-jruby
  fi

  # run group-by-environment with single jruby
  ${run_cmd}catalog-${node_name}-group-by-environment-1-jruby-timeout-0 \
    -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j 1 \
    -r ${num_environments} -s catalog-group-by-environment
  ${run_cmd}catalog-${node_name}-group-by-environment-1-jruby-timeout-unlimited \
    -c ${num_catalogs} -t unlimited -n ${node_name_and_class} -j 1 \
    -r ${num_environments} -s catalog-group-by-environment

  if [ "$num_containers" != "1" ]; then
    # run group-by-environment with configured number of jrubies
    ${run_cmd}catalog-${node_name}-group-by-environment-${num_containers}-jrubies-timeout-0 \
      -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j ${num_containers} \
      -r ${num_environments} -s catalog-group-by-environment
    ${run_cmd}catalog-${node_name}-group-by-environment-${num_containers}-jrubies-timeout-unlimited \
      -c ${num_catalogs} -t unlimited -n ${node_name_and_class} \
      -j ${num_containers} -r ${num_environments} -s catalog-group-by-environment
  fi

  # run unique-environment-per-jruby
  ${run_cmd}catalog-${node_name}-unique-environment-per-jruby-timeout-0 \
    -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j ${num_containers} \
    -r ${num_environments} -s catalog-unique-environment-per-jruby
  ${run_cmd}catalog-${node_name}-unique-environment-per-jruby-timeout-unlimited \
    -c ${num_catalogs} -t unlimited -n ${node_name_and_class} \
    -j ${num_containers} -r ${num_environments} \
    -s catalog-unique-environment-per-jruby

  set +x
}

if [[ "$deploy" == "y" ]]; then
  echo "deploying r10k environment..."
  set -x
  r10k deploy environment ${environment_name} -p -v debug -c ./dev/r10k.yaml
  set +x
fi

echo "running scenarios, outputting to: $base_output_dir..."
set -x
${run_cmd}basic-scripting-containers -c 0 -j ${num_containers} -r 0 \
   -s basic-scripting-containers
set +x

while IFS=';' read -ra nodes_arr
do
  for node in "${nodes_arr[@]}"
  do
    while IFS=',' read -ra node_arr
    do
      run_catalog_scenarios_for_node ${node_arr[0]} ${node_arr[1]}
    done <<< $node
  done
done <<< $nodes

set +x
