#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

readonly dist_dir="$1"
readonly build_number="$2"

readonly script_dir="$(dirname "$0")"

# Grab the location of the command_log file for bazel daemon so we can search it later.
readonly command_log="$("${script_dir}"/bazel info command_log)"

# Run Bazel with coverage instrumentation
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  --config=remote \
  ${auth_options} \
  --test_tag_filters=-no_linux,-no_test_linux,coverage-test,-perfgate_only \
  --define agent_coverage=true \
  -- \
  @cov//:all.suite

# We want to still attach the upsalite link if tests fail so we can't abort now
readonly bazel_test_status=$?

# Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
readonly upsalite_id="$(sed -n 's/\r$//;s/^.* invocation_id: //p' "${command_log}")"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
fi

# Abort if necessary now that the upsalite link is handled
if [[ ${bazel_test_status} -ne 0 ]]; then
  exit ${bazel_test_status}
fi

# Build the lcov file
"${script_dir}/bazel" \
  build \
  --config=remote \
  ${auth_options} \
  -- \
  @cov//:all.lcov \
  || exit $?

readonly lcov_path="./bazel-genfiles/external/cov/all/lcov"

# Generate the HTML report
genhtml -o "./out/html" ${lcov_path} -p $(pwd) --no-function-coverage || exit $?

if [[ -d "${dist_dir}" ]]; then
  # Copy the report to ab/ outputs
  mkdir "${dist_dir}/coverage" || exit $?
  cp -pv ${lcov_path} "${dist_dir}/coverage" || exit $?
  # HTML report needs to be zipped for fast uploads
  pushd "./out" || exit $?
  zip -r "html.zip" "./html" || exit $?
  popd || exit $?
  mv -v "./out/html.zip" "${dist_dir}/coverage" || exit $?

  # Upload the LCOV data to GCS if running on BYOB
  if [[ "$build_number" ]]; then
    gsutil cp ${lcov_path} "gs://android-devtools-archives/ab-studio-coverage/${build_number}/" || exit $?
  fi
fi

exit 0
