#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invalidate local cache to avoid picking up obsolete test result xmls
"${script_dir}/bazel" clean --async

config_options="--config=remote"


##########################################################################
### Part 1: Only run unreliable Sanity tests, which NOT using emulator ###
##########################################################################
# Generate a UUID for use as the bazel invocation id
readonly invocation_id_sanity_no_emu="$(uuidgen)"

target_filters=qa_sanity_unreliable,-no_linux,-no_test_linux,-perfgate_only
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id_sanity_no_emu} \
  --define=meta_android_build_number=${build_number} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  --tool_tag=${script_name} \
  -- \
  //tools/adt/idea/android-uitests/...

readonly bazel_status_sanity_no_emu=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id_sanity_no_emu}'\" />" > "${dist_dir}"/upsalite_flaky_sanity_test_results.html
fi


##########################################################################
#### Part 2: Only run unreliable Fast tests, which NOT using emulator ####
##########################################################################
# Generate an UUID for use as the bazel invocation id
readonly invocation_id_fast_no_emu="$(uuidgen)"

# Run Bazel tests, which only those requiring emulator
target_filters=qa_fast_unreliable,-no_linux,-no_test_linux,-perfgate_only
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id_fast_no_emu} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  --tool_tag=${script_name} \
  --define=meta_android_build_number=${build_number} \
  -- \
  //tools/adt/idea/android-uitests/...

readonly bazel_status_fast_no_emu=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id_fast_no_emu}'\" />" > "${dist_dir}"/upsalite_flaky_fast_test_results.html
fi


##########################################################################
##### Part 3: Only run unreliable Sanity tests, which using emulator #####
##########################################################################
# Generate a UUID for use as the bazel invocation id
readonly invocation_id_sanity_emu="$(uuidgen)"

target_filters=qa_sanity_unreliable_emu,-no_linux,-no_test_linux,-perfgate_only
QA_ANDROID_SDK_ROOT=${HOME}/Android_emulator/sdk "${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id_sanity_emu} \
  --define=meta_android_build_number=${build_number} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  --tool_tag=${script_name} \
  --define external_emulator=true \
  --define=meta_android_build_number=${build_number} \
  -- \
  //tools/adt/idea/android-uitests/...

readonly bazel_status_sanity_emu=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id_sanity_emu}'\" />" > "${dist_dir}"/upsalite_flaky_sanity_emu_test_results.html
fi


##########################################################################
###### Part 4: Only run unreliable Fast tests, which using emulator ######
##########################################################################
# Generate an UUID for use as the bazel invocation id
readonly invocation_id_fast_emu="$(uuidgen)"

# Run Bazel tests, which only those requiring emulator
target_filters=qa_fast_unreliable_emu,-no_linux,-no_test_linux,-perfgate_only
QA_ANDROID_SDK_ROOT=${HOME}/Android_emulator/sdk "${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id_fast_emu} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  --tool_tag=${script_name} \
  --define external_emulator=true \
  --define=meta_android_build_number=${build_number} \
  -- \
  //tools/adt/idea/android-uitests/...

readonly bazel_status_fast_emu=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id_fast_emu}'\" />" > "${dist_dir}"/upsalite_flaky_fast_emu_test_results.html

  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  mkdir "${dist_dir}"/testlogs
  (mv "${testlogs_dir}"/* "${dist_dir}"/testlogs/)

  echo "Remove any empty file in testlogs"
  find  "${dist_dir}"/testlogs/ -size  0 -print0 |xargs -0 rm --
fi

# See http://docs.bazel.build/versions/master/guide.html#what-exit-code-will-i-get
# Exit with status 0 if all of the above tests' exit codes is 0, 3, or 4.
for test_exit_code in "${bazel_status_sanity_no_emu}" "${bazel_status_fast_no_emu}" "${bazel_status_sanity_emu}" "${bazel_status_fast_emu}"; do
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
