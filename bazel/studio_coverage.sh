#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

readonly BAZEL_EXITCODE_TEST_FAILURES=3

readonly dist_dir="$1"
readonly build_number="$2"

if [[ $build_number =~ ^[0-9]+$ ]]; then
  readonly postsubmit=true
fi

readonly script_dir="$(dirname "$0")"

####################################
# Download a flake retry bazelrc file from GCS.
# Arguments:
#   Destination directory
# Outputs:
#   If successful, the local path; Otherwise, nothing
####################################
function download_flake_retry_rc() {
  local -r gcs_path="gs://adt-byob/known-flakes/studio-coverage/auto-retry.bazelrc"
  mkdir -p $1
  gsutil cp $gcs_path "${1}/auto-retry.bazelrc"
  if [[ $? -eq 0 ]]; then
    echo "${1}/auto-retry.bazelrc"
  fi
}

collect_and_exit() {
  local -r exit_code=$1

  if [[ -d "${dist_dir}" ]]; then
    "${script_dir}/bazel" \
    run //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector \
    --config=rcache --config=sponge \
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
  echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${dist_dir}"/upsalite_test_results.html
fi

declare -a bazelrc_flags
# For presubmit builds, try download bazelrc to deflake builds
if [[ -z "$postsubmit" && -d "${dist_dir}" ]]; then
  bazelrc=$(download_flake_retry_rc "${dist_dir}/flake-retry")
  if [[ $bazelrc ]]; then bazelrc_flags+=("--bazelrc=${bazelrc}"); fi
fi

declare -a extra_test_flags
if [[ $postsubmit ]]; then
    extra_test_flags+=(--nocache_test_results)
    extra_test_flags+=(--config=ants)
    extra_test_flags+=(--flaky_test_attempts=2)
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
  "${bazelrc_flags[@]}" \
  test \
  --config=dynamic --config=sponge \
  --invocation_id=${invocation_id} \
  --tool_tag="studio_coverage.sh" \
  --build_event_binary_file="${dist_dir:-/tmp}/bazel-${build_number}.bes" \
  --profile="${dist_dir:-/tmp}/profile-${build_number}.json.gz" \
  --build_metadata=ANDROID_BUILD_ID="${build_number}" \
  --build_metadata=ab_build_id="${build_number}" \
  --build_metadata=ab_target=studio-coverage \
  ${auth_options} \
  --test_tag_filters=-no_linux,-no_test_linux,-perfgate \
  --define agent_coverage=true \
  "${extra_test_flags[@]}" \
  -- \
  @cov//:all.suite \
  @baseline//... \
  || collect_and_exit $?

# Generate another UUID for the report invocation
readonly report_invocation_id="$(uuidgen)"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${report_invocation_id}'\" /></head>" > "${dist_dir}"/upsalite_build_report_results.html
fi

# Build the lcov file
"${script_dir}/bazel" \
  build \
  --config=rcache --config=sponge \
  --invocation_id=${report_invocation_id} \
  --jobs=HOST_CPUS*.5 \
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
