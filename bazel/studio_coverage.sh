#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

readonly BAZEL_EXITCODE_TEST_FAILURES=3

readonly dist_dir="$1"
readonly build_number="$2"

if [[ $build_number =~ ^[0-9]+$ ]]; then
  readonly postsubmit=true
fi

readonly script_dir="$(dirname "$0")"

collect_and_exit() {
  local -r exit_code=$1

  if [[ -d "${dist_dir}" ]]; then
    "${script_dir}/bazel" \
    run //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector \
    --config=rcache \
    -- \
    -bes "${dist_dir}/bazel-${build_number}.bes" \
    -testlogs "${dist_dir}/logs/junit"
  fi

  if [[ $? -ne 0 ]]; then
    echo "Bazel logs-collector failed!"
    exit $?
  fi

  # Test failures in postsubmit runs are processed by ATP from logs/junit.
  if [[ $postsubmit && $exit_code == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
    exit 0
  fi
  exit $exit_code
}

# Clean up existing results so obsolete data cannot cause issues
# --max_idle_secs is only effective at bazel server startup time so it needs to be in the first call
"${script_dir}/bazel" --max_idle_secs=60 clean --async || exit $?

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
fi

# Generate baseline coverage file lists
"${script_dir}/bazel" \
  build \
  --config=rcache \
  --build_tag_filters="coverage-sources" \
  -- \
  //tools/... \
  || exit $?

# Run Bazel with coverage instrumentation
"${script_dir}/bazel" \
  test \
  --keep_going \
  --config=dynamic \
  --invocation_id=${invocation_id} \
  --build_event_binary_file="${dist_dir:-/tmp}/bazel-${build_number}.bes" \
  --profile="${dist_dir:-/tmp}/profile-${build_number}.json.gz" \
  ${auth_options} \
  --test_tag_filters=-no_linux,-no_test_linux,-perfgate \
  --define agent_coverage=true \
  -- \
  @cov//:all.suite \
  @baseline//... \
  || collect_and_exit $?

# Generate another UUID for the report invocation
readonly report_invocation_id="$(uuidgen)"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${report_invocation_id}'\" />" > "${dist_dir}"/upsalite_build_report_results.html
fi

# Build the lcov file
"${script_dir}/bazel" \
  build \
  --config=rcache \
  --remote_download_outputs=toplevel \
  --invocation_id=${report_invocation_id} \
  ${auth_options} \
  -- \
  @cov//:comps.lcov_all \
  @cov//:comps.list_all \
  || exit $?

readonly lcov_path="./bazel-bin/external/cov/comps/lcov"
readonly comp_list_path="./bazel-bin/external/cov/comps/list"

# Generate the HTML report
#genhtml -o "./out/html" ${lcov_path} -p $(pwd) --no-function-coverage || exit $?

if [[ -d "${dist_dir}" ]]; then
  # Copy the report to ab/ outputs
  mkdir "${dist_dir}/coverage" || exit $?
  cp -pv ${lcov_path} "${dist_dir}/coverage" || exit $?
  cp -pv ${comp_list_path} "${dist_dir}/coverage" || exit $?
  # HTML report needs to be zipped for fast uploads
  #pushd "./out" || exit $?
  #zip -r "html.zip" "./html" || exit $?
  #popd || exit $?
  #mv -v "./out/html.zip" "${dist_dir}/coverage" || exit $?

  # Upload the LCOV data to GCS if running on BYOB but only for postsubmit builds
  if [[ "$build_number" && "$postsubmit" ]]; then
    # TODO(b/171261837) remove hardcoded gsutil path
    /snap/bin/gsutil cp ${lcov_path} "gs://android-devtools-archives/ab-studio-coverage/${build_number}/" || exit $?
    /snap/bin/gsutil cp ${comp_list_path} "gs://android-devtools-archives/ab-studio-coverage/${build_number}/" || exit $?
  fi
fi

collect_and_exit 0
