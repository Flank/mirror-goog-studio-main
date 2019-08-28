#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

readonly lsb_release="$(grep -oP '(?<=DISTRIB_CODENAME=).*' /etc/lsb-release)"
readonly crostini_timestamp_file="/buildbot/lastrun.out"

# Invalidate local cache to avoid picking up obsolete test result xmls
"${script_dir}/bazel" clean --async

#Have crostini tests run locally and one at a time
if [[ $lsb_release == "crostini" ]]; then
  config_options="--config=cloud_resultstore"
  target_filters=qa_sanity,-qa_unreliable,-no_linux,-no_test_linux,-requires_emulator,-perfgate_only,-no_crostini

  current_time=$(date +"%s")

  #This prevents builds from ocurring if the last one was started less than three hours ago
  #so the chromebox doesn't become too bogged down
  if [[ -f "${crostini_timestamp_file}" ]]; then
    last_run_time=$(cat $crostini_timestamp_file)
    #if the last build occurred less than three hours ago it exits
    if [[ $(($current_time-$last_run_time)) -lt 10800 ]]; then
      exit 0
    fi
  fi
  echo $current_time > $crostini_timestamp_file

  # Generate a UUID for use as the bazel invocation id
  readonly build_invocation_id="$(uuidgen)"

  #Build the project in parallel for crostini
  "${script_dir}/bazel" \
    --max_idle_secs=60 \
    build \
    ${config_options} \
    --invocation_id=${build_invocation_id} \
    --define=meta_android_build_number=${build_number} \
    --build_tag_filters=${target_filters} \
    --tool_tag=${script_name} \
    -- \
    //tools/adt/idea/android-uitests/...

  readonly test_invocation_id="$(uuidgen)"

  #Run the tests one at a time for crostini
  "${script_dir}/bazel" \
    --max_idle_secs=60 \
    test \
    --keep_going \
    ${config_options} \
    --jobs=1 \
    --invocation_id=${test_invocation_id} \
    --define=meta_android_build_number=${build_number} \
    --build_tag_filters=${target_filters} \
    --test_tag_filters=${target_filters} \
    --tool_tag=${script_name} \
    -- \
    //tools/adt/idea/android-uitests/...

  readonly bazel_status_no_emu=$?

  if [[ -d "${dist_dir}" ]]; then
    echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${test_invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
  fi

  readonly bazel_status_emu=0
else #Executes normally on linux as before
  config_options="--config=remote"

  # Generate a UUID for use as the bazel invocation id
  readonly invocation_id="$(uuidgen)"

  # The sanity tests are ran in 2 groups.
  # The first group is all the tests that do not use an Android emulator.
  # The second group is all the tests that use the Android emulator.
  # We need to run in 2 groups because the 2 sets of tests run with different
  # options.

  # Run Bazel tests - no emulator tests should run here
  target_filters=qa_sanity,-qa_unreliable,-no_linux,-no_test_linux,-requires_emulator,-perfgate_only
  "${script_dir}/bazel" \
    --max_idle_secs=60 \
    test \
    --keep_going \
    ${config_options} \
    --invocation_id=${invocation_id} \
    --define=meta_android_build_number=${build_number} \
    --build_tag_filters=${target_filters} \
    --test_tag_filters=${target_filters} \
    --tool_tag=${script_name} \
    -- \
    //tools/adt/idea/android-uitests/...

  readonly bazel_status_no_emu=$?

  if [[ -d "${dist_dir}" ]]; then
    echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
  fi

  # Generate a UUID for use as the bazel invocation id
  readonly invocation_id_emu="$(uuidgen)"

  # Run Bazel tests - only emulator tests should run here
  target_filters=qa_sanity_emu,-qa_unreliable,-no_linux,-no_test_linux
  QA_ANDROID_SDK_ROOT=${HOME}/Android_emulator/sdk "${script_dir}/bazel" \
    --max_idle_secs=60 \
    test \
    --keep_going \
    ${config_options} \
    --invocation_id=${invocation_id_emu} \
    --build_tag_filters=${target_filters} \
    --test_tag_filters=${target_filters} \
    --tool_tag=${script_name} \
    --define external_emulator=true \
    --define=meta_android_build_number=${build_number} \
    -- \
    //tools/adt/idea/android-uitests/...

  readonly bazel_status_emu=$?

  if [[ -d "${dist_dir}" ]]; then
    echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id_emu}'\" />" > "${dist_dir}"/upsalite_emu_test_results.html
  fi
fi

if [[ -d "${dist_dir}" ]]; then
  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  mkdir "${dist_dir}"/testlogs
  (mv "${testlogs_dir}"/* "${dist_dir}"/testlogs/)

  echo "Remove any empty file in testlogs"
  find  "${dist_dir}"/testlogs/ -size  0 -print0 |xargs -0 rm --

fi

# See http://docs.bazel.build/versions/master/guide.html#what-exit-code-will-i-get
# Exit with status 0 if all of the above tests' exit codes is 0, 3, or 4.
for test_exit_code in "${bazel_status_no_emu}" "${bazel_status_emu}"; do
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
