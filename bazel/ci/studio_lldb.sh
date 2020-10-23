#!/bin/bash -x
#
# Tests LLDB integration tests.

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"

readonly script_dir="$(dirname "$0")"
readonly bazel_dir="${script_dir}/.."
readonly script_name="$(basename "$0")"
readonly invocation_id="$(uuidgen)"
readonly config_options="--config=dynamic"
readonly bin_dir="$("${bazel_dir}"/bazel info ${config_options} bazel-bin)"
readonly java="${script_dir}/../../../../prebuilts/studio/jdk/linux/jre/bin/java"

"${bazel_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  ${config_options} \
  --invocation_id=${invocation_id} \
  --tool_tag=${script_name} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --flaky_test_attempts=3 \
  //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector_deploy.jar \
  //tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests

readonly bazel_status=$?

if [[ -d "${DIST_DIR}" ]]; then

  # Generate a HTML page redirecting to test results.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${DIST_DIR}"/upsalite_test_results.html

  # Generate logs/junit/logs-summary.xml file that will be consumed by ATP.
  ${java} -jar "${bin_dir}/tools/vendor/adt_infra_internal/rbe/logscollector/logs-collector_deploy.jar" \
    -bes "${DIST_DIR}/bazel-${BUILD_NUMBER}.bes" \
    -testlogs "${DIST_DIR}/logs/junit"
fi

BAZEL_EXITCODE_TEST_FAILURES=3

# For post-submit builds, if the tests fail we still want to report success
# so that musket/bayonet don't aggressively turn the target/branch down
# after repeated failures.
if [[ $bazel_status == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $bazel_status
fi
