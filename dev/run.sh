#!/bin/bash

set -e

base_output_dir=./target/mem-measure/`date +"%Y%m%d-%H%M%S"`
deploy="y"
environment_name="20160808_SERVER_1448_catalog_memory_measurement_with_hiera"
lein_run_cmd="go"
master_conf_dir="./target/master-conf-dir"
env_dir="./target/master-code-dir/environments"
num_catalogs="5"
num_containers="5"
num_environments="5"
profile="jruby17"
skip_basic_container_scenarios="n"

default_node_names=(empty small)
nodes=
for node_name in ${default_node_names[@]}
do
  nodes="${nodes}${node_name},role::by_size::${node_name};"
done

while getopts ":c:e:f:ij:n:o:p:r:s" opt; do
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
     p)
       profile="$OPTARG";;
     r)
       num_environments="$OPTARG";;
     s)
       skip_basic_container_scenarios="y";;
     \?)
       echo "Invalid option: -$OPTARG" >&2
       exit 1;;
     :)
       echo "Option -$OPTARG requires an argument." >&2
       exit 1;;
   esac
 done

run_cmd="lein with-profile ${profile} ${lein_run_cmd} -- "\
"-e ${environment_name} -o ${base_output_dir}/"

run_catalog_scenarios_for_node()
{
  node_name=$1
  node_class=$2
  node_name_and_class=$1,$2

  set -x

  # run one-jruby-one-environment
  ${run_cmd}catalog-${node_name}-group-by-catalog-timeout-0 \
    -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j 1 -r 1 \
    -s catalog-group-by-catalog
  ${run_cmd}catalog-${node_name}-group-by-catalog-timeout-unlimited \
    -c ${num_catalogs} -t unlimited -n ${node_name_and_class} -j 1 -r 1 \
    -s catalog-group-by-catalog

  # run group-by-jruby with single environment and single catalog
  ${run_cmd}catalog-${node_name}-group-by-jruby-1-env-1-catalog-timeout-0 \
    -c 1 -t 0 -n ${node_name_and_class} \
    -j ${num_containers} -r 1 -s catalog-group-by-jruby
  ${run_cmd}catalog-${node_name}-group-by-jruby-1-env-1-catalog-timeout-unlimited \
    -c 1 -t unlimited -n ${node_name_and_class} \
    -j ${num_containers} -r 1 -s catalog-group-by-jruby

  set +x

  if [ "$num_catalogs" != "1" ]; then
    set -x
    # run group-by-jruby with single environment and multiple catalogs
    ${run_cmd}catalog-${node_name}-group-by-jruby-1-env-${num_catalogs}-catalogs-timeout-0 \
      -c ${num_catalogs} -t 0 -n ${node_name_and_class} \
      -j ${num_containers} -r 1 -s catalog-group-by-jruby
    ${run_cmd}catalog-${node_name}-group-by-jruby-1-env-${num_catalogs}-catalogs-timeout-unlimited \
      -c ${num_catalogs} -t unlimited -n ${node_name_and_class} \
      -j ${num_containers} -r 1 -s catalog-group-by-jruby
    set +x
  fi
  
  if [ "$num_environments" != "1" ] && [ "$num_environments" != "1" ]; then
    # run group-by-jruby with multiple environments and multiple catalogs
    set -x
    ${run_cmd}catalog-${node_name}-group-by-jruby-${num_environments}-envs-${num_catalogs}-catalogs-timeout-0 \
      -c ${num_catalogs} -t 0 -n ${node_name_and_class} \
      -j ${num_containers} -r ${num_environments} -s catalog-group-by-jruby
    ${run_cmd}catalog-${node_name}-group-by-jruby-${num_environments}-envs-${num_catalogs}-catalogs-timeout-unlimited \
      -c ${num_catalogs} -t unlimited -n ${node_name_and_class} \
      -j ${num_containers} -r ${num_environments} -s catalog-group-by-jruby
    set +x
  fi

  set -x

  # run group-by-environment with single jruby
  ${run_cmd}catalog-${node_name}-group-by-environment-1-jruby-timeout-0 \
    -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j 1 \
    -r ${num_environments} -s catalog-group-by-environment
  ${run_cmd}catalog-${node_name}-group-by-environment-1-jruby-timeout-unlimited \
    -c ${num_catalogs} -t unlimited -n ${node_name_and_class} -j 1 \
    -r ${num_environments} -s catalog-group-by-environment

  set +x

  if [ "$num_containers" != "1" ]; then
    set -x
    # run group-by-environment with multiple jrubies
    ${run_cmd}catalog-${node_name}-group-by-environment-${num_containers}-jrubies-timeout-0 \
      -c ${num_catalogs} -t 0 -n ${node_name_and_class} -j ${num_containers} \
      -r ${num_environments} -s catalog-group-by-environment
    ${run_cmd}catalog-${node_name}-group-by-environment-${num_containers}-jrubies-timeout-unlimited \
      -c ${num_catalogs} -t unlimited -n ${node_name_and_class} \
      -j ${num_containers} -r ${num_environments} -s catalog-group-by-environment
   set +x
  fi

  set -x

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
  mkdir -p "${master_conf_dir}"
  sed "s/.*:datadir:.*/  :datadir: ${env_dir//\//\\/}\/%{environment}\/hieradata/g" \
    "${env_dir}/${environment_name}/root_files/hiera.yaml" >\
    "${master_conf_dir}/hiera.yaml"
  set +x
fi

echo "running scenarios, outputting to: $base_output_dir..."

if [[ "$skip_basic_container_scenarios" == "n" ]]; then
  set -x
  ${run_cmd}basic-scripting-containers -c 0 -j ${num_containers} -r 0 \
    -s basic-scripting-containers
  set +x
fi

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

echo "summarizing results..."
set -x
lein summarize -d ${base_output_dir} -o ${base_output_dir}/${base_output_dir##*/}-summary.json
set +x
