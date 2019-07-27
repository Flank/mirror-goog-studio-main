#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

build_tag_filters=-no_linux
test_tag_filters=perfgate_multi_run,perfgate_only,-no_perfgate,-no_linux,-no_test_linux

config_options="--config=remote"

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

# Run Bazel
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --define=meta_android_build_number=${build_number} \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=${script_name} \
  --profile=${dist_dir}/perfgate-profile-${build_number}.json \
  --runs_per_test=5 \
  -- \
  $(< "${script_dir}/targets")

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then

  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"

  # Upload all test logs
  find "${testlogs_dir}" -type f -name outputs.zip -exec zip -r "${dist_dir}/bazel_test_logs.zip" {} \;

  # Filter test logs for performance metrics upload.
  find "${testlogs_dir}" -type f -name outputs.zip -exec zip -d {} \*.gz \;
  # Upload perfgate performance files
  find "${testlogs_dir}" -type f -name outputs.zip -exec zip -r "${dist_dir}/perfgate_data.zip" {} \;

fi

# Clean up the Bazel output directory.
"${script_dir}/bazel" clean

exit $bazel_status
