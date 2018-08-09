#!/bin/bash -ex
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
"${script_dir}/bazel" --max_idle_secs=60 --bazelrc=/dev/null test --keep_going --bes_backend=buildeventservice.googleapis.com --auth_credentials="$HOME"/.android-studio-alphasource.json --auth_scope=https://www.googleapis.com/auth/cloud-source-tools --project_id=908081808034 --config=remote --cache_test_results=no --build_tag_filters=-no_linux --test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable $(< "${script_dir}/targets")

if [[ -d "${dist_dir}" ]]; then
  # on AB/ATP, follow conventions to use gtest-testlog-forwarding
  readonly testlogs_dir="$(${script_dir}/bazel info bazel-testlogs)"
  mkdir "${dist_dir}"/gtest
  # This does not handle spaces in file names.
  for xml_source in $(cd "${testlogs_dir}" && find -name '*.xml' -printf '%P\n'); do
    xml_target="$(echo "${xml_source}" | tr '/' '_')"
    cp -pv "${testlogs_dir}/${xml_source}" "${dist_dir}/gtest/${xml_target}"
  done
fi
