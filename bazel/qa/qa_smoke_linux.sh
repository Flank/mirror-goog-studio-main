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
"${script_dir}/../bazel" clean --async  --expunge

#Have crostini tests run locally and one at a time
if [[ $lsb_release == "crostini" ]]; then
  # don't use any remote cached items, some items built on Linux may not be compatible. b/172365127
  config_options="--config=resultstore"
  target_filters=qa_smoke,ui_test,-qa_unreliable,-no_linux,-no_test_linux,-requires_emulator,-no_crostini

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

  # Temp workaround for b/159371003
  # Check running processes
  ps -ef
  readonly counter="$(ps -ef | grep -c 'at-spi-bus-launcher')"
  # these accessibiluty daemons keep on accumulating with each test execution
  # and ultimately cause OOM failures https://paste.googleplex.com/4715109898256384
  # manually kill them off for now
  ps -ef | grep "at-spi-bus-launcher" | awk '{print $2}' | xargs kill -9
  ps -ef | grep "at-spi2/accessibility.conf" | awk '{print $2}' | xargs kill -9
  ps -ef | grep "/usr/bin/dbus-daemon --syslog-only" | awk '{print $2}' | xargs kill -9

  # Generate a UUID for use as the bazel invocation id
  readonly logs_collector_invocation_id="$(uuidgen)"

  #Build  logs collector jar
  "${script_dir}/../bazel" \
    --max_idle_secs=60 \
    build \
    ${config_options} \
    --invocation_id=${logs_collector_invocation_id} \
    --define=meta_android_build_number=${build_number} \
    --tool_tag=${script_name} \
    -- \
    //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector_deploy.jar

  readonly test_invocation_id="$(uuidgen)"

  # Run the tests one at a time after all dependencies get built
  # Also limit # of jobs running, this should be based in available resources
  "${script_dir}/../bazel" \
    --max_idle_secs=60 \
    test \
    --keep_going \
    ${config_options} \
    --test_strategy=exclusive \
    --jobs=8 \
    --worker_verbose=true \
    --invocation_id=${test_invocation_id} \
    --define=meta_android_build_number=${build_number} \
    --build_event_binary_file="${dist_dir:-/tmp}/bazel-${build_number}.bes" \
    --build_tag_filters=${target_filters} \
    --test_tag_filters=${target_filters} \
    --tool_tag=${script_name} \
    --flaky_test_attempts=//tools/adt/idea/android-uitests:.*@2 \
    -- \
    //tools/adt/idea/android-uitests/...

  readonly bazel_status_no_emu=$?

  if [[ -d "${dist_dir}" ]]; then
    echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${test_invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
  fi

  readonly bazel_status_emu=0

  readonly java="prebuilts/studio/jdk/linux/jre/bin/java"
  readonly bin_dir="$("${script_dir}"/../bazel info ${config_options} bazel-bin)"

  # Generate the perfgate zip from the test bes
  # Copy it as part of build artifacts under dist_dir
  ${java} -jar "${bin_dir}/tools/vendor/adt_infra_internal/rbe/logscollector/logs-collector_deploy.jar" \
    -bes "${dist_dir}/bazel-${build_number}.bes" \
    -perfzip "${dist_dir}/perfgate_data.zip"

else #Executes normally on linux as before
  config_options="--config=remote"

  # Generate a UUID for use as the bazel invocation id
  readonly invocation_id="$(uuidgen)"

  # The smoke tests are ran in 2 groups.
  # The first group is all the tests that do not use an Android emulator.
  # The second group is all the tests that use the Android emulator.
  # We need to run in 2 groups because the 2 sets of tests run with different
  # options.

  # Run Bazel tests - no emulator tests should run here
  target_filters=qa_smoke,ui_test,-qa_unreliable,-no_linux,-no_test_linux,-requires_emulator
  "${script_dir}/../bazel" \
    --max_idle_secs=60 \
    test \
    --keep_going \
    ${config_options} \
    --invocation_id=${invocation_id} \
    --define=meta_android_build_number=${build_number} \
    --build_tag_filters=${target_filters} \
    --test_tag_filters=${target_filters} \
    --tool_tag=${script_name} \
    --flaky_test_attempts=//tools/adt/idea/android-uitests:.*@2 \
    -- \
    //tools/adt/idea/android-uitests/...

  readonly bazel_status_no_emu=$?

  if [[ -d "${dist_dir}" ]]; then
    echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
  fi
fi

if [[ -d "${dist_dir}" ]]; then
  readonly testlogs_dir="$("${script_dir}/../bazel" info bazel-testlogs ${config_options})"
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
