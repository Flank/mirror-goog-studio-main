#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

# TODO: Remove this block once we have enough Mac bots to run all
# post-submit builds
# Use flag file, skip all builds within 12 hours of last ran build
# Skipped builds are marked green (exit 0), in ab/
readonly mac_timestamp_file="/buildbot/lastrun.out"
current_time=$(date +"%s")
if [[ -f "${mac_timestamp_file}" ]]; then
  last_run_time=$(cat $mac_timestamp_file)
  if [[ $(($current_time-$last_run_time)) -lt 10800 ]]; then
    exit 0
  fi
fi
# Update timestamp in the flag file
echo $current_time > $mac_timestamp_file

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invocation ID must be lower case in Upsalite URL
readonly invocation_id=$(uuidgen | tr A-F a-f)

readonly config_options="--config=local --config=release --config=resultstore"
readonly target_filters="qa_smoke,ui_test,-qa_unreliable,-no_mac,-no_test_mac,-requires_emulator"

# Use test strategy to run 1 test at a time after all build dependencies are built
"${script_dir}/../bazel" \
        --max_idle_secs=60 \
        test \
        --keep_going \
        ${config_options} \
        --test_strategy=exclusive \
        --spawn_strategy=local \
        --invocation_id=${invocation_id} \
        --define=meta_android_build_number=${build_number} \
        --build_tag_filters=${target_filters} \
        --test_tag_filters=${target_filters} \
        --tool_tag=${script_name} \
        --flaky_test_attempts=//tools/adt/idea/android-uitests:.*@2 \
        -- \
        //tools/adt/idea/android-uitests/...

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  readonly testlogs_dir="$("${script_dir}/../bazel" info --config=release bazel-testlogs)"
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
