#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"

config_options="--config=postsubmit"

# Conditionally add --auth_credentials option for BYOB machines.
if [[ -r "${HOME}/.android-studio-alphasource.json" ]]; then
  config_options="${config_options} --config=remote --auth_credentials=${HOME}/.android-studio-alphasource.json"
fi

# Grab the location of the command_log file for bazel daemon so we can search it later.
readonly command_log="$("${script_dir}"/bazel info ${config_options} command_log)"

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
  ${config_options} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  -- \
  //tools/adt/idea/android-uitests/...

readonly bazel_status_no_emu=$?

if [[ -d "${dist_dir}" ]]; then
  # Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
  readonly upsalite_id="$(sed -n 's/\r$//;s/^.* invocation_id: //p' "${command_log}")"
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
fi

# Run Bazel tests - only emulator tests should run here
target_filters=qa_sanity_emu,-qa_unreliable,-no_linux,-no_test_linux
QA_ANDROID_SDK_ROOT=${HOME}/Android_emulator/sdk "${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  ${config_options} \
  --build_tag_filters=${target_filters} \
  --test_tag_filters=${target_filters} \
  --jobs=1 \
  --remote_local_fallback_strategy=sandboxed \
  --define external_emulator=true \
  -- \
  //tools/adt/idea/android-uitests/...

readonly bazel_status_emu=$?

if [[ -d "${dist_dir}" ]]; then
  # Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
  readonly upsalite_id_emu="$(sed -n 's/\r$//;s/^.* invocation_id: //p' "${command_log}")"
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id_emu}'\" />" > "${dist_dir}"/upsalite_emu_test_results.html

  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  mkdir "${dist_dir}"/bazel-testlogs
  (cd "${testlogs_dir}" && zip -R "${dist_dir}"/bazel-testlogs/xml_files.zip "*.xml")

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
