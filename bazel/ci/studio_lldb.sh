#!/bin/bash -x
#
# Tests LLDB integration tests.

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"

readonly script_dir="$(dirname "$0")"
readonly bazel_dir="${script_dir}/.."
readonly script_name="$(basename "$0")"
readonly invocation_id="$(uuidgen)"
readonly config_options="--config=ci --config=ants"
readonly bin_dir="$("${bazel_dir}"/bazel info ${config_options} bazel-bin)"

"${bazel_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  ${config_options} \
  --invocation_id=${invocation_id} \
  --tool_tag=${script_name} \
  --build_metadata="ab_build_id=${BUILD_NUMBER}" \
  --build_metadata="ab_target=studio-lldb" \
  --build_metadata="test_definition_name=android_studio/studio_lldb" \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --flaky_test_attempts=3 \
  //tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests

readonly bazel_status=$?

if [[ -d "${DIST_DIR}" ]]; then

  # Generate a HTML page redirecting to test results.
  echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${DIST_DIR}"/upsalite_test_results.html
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
