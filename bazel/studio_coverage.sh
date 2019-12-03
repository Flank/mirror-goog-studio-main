#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

readonly dist_dir="$1"
readonly build_number="$2"

readonly script_dir="$(dirname "$0")"

# Clean up existing results so obsolete data cannot cause issues
"${script_dir}/bazel" clean --async || exit $?

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
fi

# Run Bazel with coverage instrumentation
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  --config=remote \
  --remote_download_outputs=toplevel \
  --invocation_id=${invocation_id} \
  ${auth_options} \
  --test_tag_filters=-no_linux,-no_test_linux,coverage-test,-perfgate \
  --define agent_coverage=true \
  -- \
  @cov//:all.suite \
  || exit $?

# Generate another UUID for the report invocation
readonly report_invocation_id="$(uuidgen)"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${report_invocation_id}'\" />" > "${dist_dir}"/upsalite_build_report_results.html
fi

# Build the lcov file
"${script_dir}/bazel" \
  build \
  --config=remote \
  --remote_download_outputs=toplevel \
  --invocation_id=${report_invocation_id} \
  ${auth_options} \
  -- \
  @cov//:all.lcov \
  @cov//:comps.list_all \
  || exit $?

readonly lcov_path="./bazel-bin/external/cov/all/lcov"
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

  # Upload the LCOV data to GCS if running on BYOB
  if [[ "$build_number" ]]; then
    gsutil cp ${lcov_path} "gs://android-devtools-archives/ab-studio-coverage/${build_number}/" || exit $?
    gsutil cp ${comp_list_path} "gs://android-devtools-archives/ab-studio-coverage/${build_number}/" || exit $?
  fi
fi

exit 0
