#!/bin/bash -x
#
# Tests LLDB integration tests.
readonly script_dir="$(dirname "$0")"
readonly bazel_dir="${script_dir}/.."
readonly script_name="$(basename "$0")"
readonly invocation_id="$(uuidgen)"

"${bazel_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --config=dynamic \
  --invocation_id=${invocation_id} \
  --tool_tag=${script_name} \
  //tools/vendor/google/lldb-integration-tests:lldb-integration-tests_tests

readonly bazel_status=$?

if [[ -d "${DIST_DIR}" ]]; then
  # Generate a HTML page redirecting to test results.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${DIST_DIR}"/upsalite_test_results.html

fi

exit $bazel_status
