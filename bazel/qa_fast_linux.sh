#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"

config_options="--config=remote"

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

# Run FAST Bazel tests
target_filters=qa_fast,-qa_unreliable,-no_linux,-no_test_linux,-requires_emulator,-perfgate_only
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  -- \
  //tools/adt/idea/android-uitests/...

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
  
  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  mkdir "${dist_dir}"/testlogs
  (mv "${testlogs_dir}"/* "${dist_dir}"/testlogs/)

  echo "Remove any empty file in testlogs"
  find  "${dist_dir}"/testlogs/ -size  0 -print0 |xargs -0 rm --

fi

# See http://docs.bazel.build/versions/master/guide.html#what-exit-code-will-i-get
# Exit with status 0 if all of the above tests' exit codes is 0, 3, or 4.
for test_exit_code in "${bazel_status}"; do
  case $test_exit_code in
    [034])
      # Exit code 0: successful test run
      # Exit code 3: tests failed or timed out. We ignore test failures for
      # manual review
      # Exit code 4: No tests found. This can happen if all tests are moved out
      # of the reliable group.
      ;;
    *)
      exit $test_exit_code
  esac
done

exit 0
