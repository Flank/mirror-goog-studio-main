#!/bin/bash -x
#
# Build and test a set of targets via bazel using RBE.

readonly ARGV=("$@")

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
readonly BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"
# AS_BUILD_NUMBER is the same as BUILD_NUMBER but omits the P for presubmit,
# to satisfy Integer.parseInt in BuildNumber.parseBuildNumber
# The "#P" matches "P" only at the beginning of BUILD_NUMBER
readonly AS_BUILD_NUMBER="${BUILD_NUMBER/#P/0}"

if [[ $BUILD_NUMBER == "SNAPSHOT" ]];
then
  readonly BUILD_TYPE="LOCAL"
elif [[ $BUILD_NUMBER =~ ^P[0-9]+$ ]];
then
  readonly BUILD_TYPE="PRESUBMIT"
else
  readonly BUILD_TYPE="POSTSUBMIT"
fi

readonly SCRIPT_DIR="$(dirname "$0")"
readonly SCRIPT_NAME="$(basename "$0")"

readonly CONFIG_OPTIONS="--config=ci"

####################################
# Copies bazel artifacts to an output directory named 'artifacts'.
# Globals:
#   DIST_DIR
#   SCRIPT_DIR
#   CONFIG_OPTIONS
# Arguments:
#   None
####################################
function copy_bazel_artifacts() {(
  set -e
  local -r artifacts_dir="${DIST_DIR}/artifacts"
  mkdir -p ${artifacts_dir}
  local -r bin_dir="$("${SCRIPT_DIR}"/bazel info ${CONFIG_OPTIONS} bazel-bin)"

  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.linux.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.win.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.mac.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.mac_arm.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio_build_manifest.textproto ${artifacts_dir}/android-studio_build_manifest.textproto
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio_update_message.html ${artifacts_dir}/android-studio_update_message.html
  cp -a ${bin_dir}/tools/adt/idea/studio/updater_deploy.jar ${artifacts_dir}/android-studio-updater.jar
  cp -a ${bin_dir}/tools/adt/idea/updater-ui/sdk-patcher.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/native/installer/android-studio-bundle-data.zip ${artifacts_dir}

  cp -a ${bin_dir}/tools/base/dynamic-layout-inspector/skia/skiaparser.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/sdklib/commandlinetools_*.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/ddmlib/tools.ddmlib.jar ${artifacts_dir}/ddmlib.jar
  cp -a ${bin_dir}/tools/base/ddmlib/libincfs.jar ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/lint/libs/lint-tests/lint-tests.jar ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/deploy/deployer/deployer.runner_deploy.jar ${artifacts_dir}/deployer.jar
  cp -a ${bin_dir}/tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon ${artifacts_dir}
  cp -a ${bin_dir}/tools/vendor/google/game-tools/packaging/game-tools-linux.tar.gz ${artifacts_dir}
  cp -a ${bin_dir}/tools/vendor/google/game-tools/packaging/game-tools-win.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/deploy/service/deploy.service_deploy.jar ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/gmaven/gmaven.zip ${artifacts_dir}/gmaven_repo.zip
  cp -a ${bin_dir}/tools/base/build-system/documentation.zip ${artifacts_dir}/android_gradle_plugin_reference_docs.zip
)}

####################################
# Generates flag values and runs bazel test.
# Globals:
#   ARGV
#   AS_BUILD_NUMBER
#   BUILD_NUMBER
#   BUILD_TYPE
#   CONFIG_OPTIONS
#   DIST_DIR
#   SCRIPT_DIR
#   SCRIPT_NAME
# Arguments:
#   None
####################################
function run_bazel_test() {
  if [[ $BUILD_TYPE == "LOCAL" ]];
  then
    # Assuming manual user invocation and using limited host resources.
    # This should prevent bazel from causing the host to freeze due to using
    # too much memory.
    local -r worker_instances=2
  else
    local -r worker_instances=auto
  fi

  local build_tag_filters=-no_linux
  local test_tag_filters=-no_linux,-no_test_linux,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate,-very_flaky
  local target_name="studio-linux"

  declare -a conditional_flags
  if [[ " ${ARGV[@]} " =~ " --detect_flakes " ]];
  then
    conditional_flags+=(--flaky_test_attempts=3)
    conditional_flags+=(--nocache_test_results)
    conditional_flags+=(--build_tests_only)
  # Only run tests tagged with `very_flaky`, this is different than tests using
  # the 'Flaky' attribute/tag. Tests that are excessively flaky use this tag to
  # avoid running in presubmit.
  elif [[ " ${ARGV[@]} " =~ " --very_flaky " ]];
  then
    conditional_flags+=(--build_tests_only)
    target_name="studio-linux_very_flaky"
    test_tag_filters=-no_linux,-no_test_linux,very_flaky
  elif [[ $BUILD_TYPE == "POSTSUBMIT" ]]; then
    conditional_flags+=(--bes_keywords=ab-postsubmit)
    conditional_flags+=(--nocache_test_results)
    conditional_flags+=(--flaky_test_attempts=2)
  fi

  # Generate a UUID for use as the bazel test invocation id
  local -r invocation_id="$(uuidgen)"

  if [[ -d "${DIST_DIR}" ]]; then
    # Generate a simple html page that redirects to the test results page.
    echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${DIST_DIR}"/upsalite_test_results.html
  fi

  # Workaround: This invocation [ab]uses --runs_per_test to disable caching for the
  # iml_to_build_consistency_test see https://github.com/bazelbuild/bazel/issues/6038
  # This has the side effect of running it twice, but as it only takes a few seconds that seems ok.
  local -r extra_test_flags=(--runs_per_test=//tools/base/bazel:iml_to_build_consistency_test@2)

  # Run Bazel
  "${SCRIPT_DIR}/bazel" \
    --max_idle_secs=60 \
    test \
    ${CONFIG_OPTIONS} --config=ants \
    --worker_max_instances=${worker_instances} \
    --invocation_id=${invocation_id} \
    --build_tag_filters=${build_tag_filters} \
    --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
    --define=meta_android_build_number="${BUILD_NUMBER}" \
    --build_metadata=ANDROID_BUILD_ID="${BUILD_NUMBER}" \
    --build_metadata=ANDROID_TEST_INVESTIGATE="http://ab/tests/bazel/${invocation_id}" \
    --build_metadata=ab_build_id="${BUILD_NUMBER}" \
    --build_metadata=ab_target="${target_name}" \
    --test_tag_filters=${test_tag_filters} \
    --tool_tag=${SCRIPT_NAME} \
    --embed_label="${AS_BUILD_NUMBER}" \
    --profile="${DIST_DIR:-/tmp}/profile-${BUILD_NUMBER}.json.gz" \
    --jobs=500 \
    "${extra_test_flags[@]}" \
    "${conditional_flags[@]}" \
    -- \
    //tools/adt/idea/studio:android-studio \
    //tools/adt/idea/studio:updater_deploy.jar \
    //tools/adt/idea/updater-ui:sdk-patcher.zip \
    //tools/adt/idea/native/installer:android-studio-bundle-data \
    //tools/base/profiler/native/trace_processor_daemon \
    //tools/base/deploy/deployer:deployer.runner_deploy.jar \
    //tools/adt/idea/studio:test_studio \
    //tools/vendor/google/game-tools/packaging:packaging-linux \
    //tools/vendor/google/game-tools/packaging:packaging-win \
    //tools/base/deploy/service:deploy.service_deploy.jar \
    //tools/base/ddmlib:tools.ddmlib \
    //tools/base/ddmlib:incfs \
    //tools/base/lint/libs/lint-tests:lint-tests \
    //tools/base/bazel:local_maven_repository_generator_deploy.jar \
    //tools/base/build-system:documentation.zip \
    $(< "${SCRIPT_DIR}/targets")
}

####################################
# Copies bazel worker logs to an output directory 'bazel_logs'.
# Globals:
#   BUILD_NUMBER
#   DIST_DIR
#   SCRIPT_DIR
####################################
function copy_bazel_worker_logs() {
  local -r output_base="$(${SCRIPT_DIR}/bazel info output_base)"
  local -r worker_log_dir="${DIST_DIR:-/tmp/${BUILD_NUMBER}/studio_linux}/bazel_logs"
  mkdir -p "${worker_log_dir}"
  cp "${output_base}/bazel-workers/*.log" "${worker_log_dir}"
}

####################################
# Runs the logs collector to create perfgate data and test-summary.
# Globals:
#   BUILD_NUMBER
#   BUILD_TYPE
#   CONFIG_OPTIONS
#   DIST_DIR
#   SCRIPT_DIR
####################################
function collect_logs() {
  if [[ $BUILD_TYPE == "POSTSUBMIT" ]]; then
    local -r perfgate_arg="-perfzip \"${DIST_DIR}/perfgate_data.zip\""
  else
    local -r perfgate_arg=""
  fi

  "${SCRIPT_DIR}/bazel" \
    run //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector \
    ${CONFIG_OPTIONS} \
    -- \
    -bes "${DIST_DIR}/bazel-${BUILD_NUMBER}.bes" \
    -testlogs "${DIST_DIR}/logs/junit" \
    ${perfgate_arg}
}

run_bazel_test
readonly BAZEL_STATUS=$?

readonly BAZEL_EXITCODE_SUCCESS=0
readonly BAZEL_EXITCODE_TEST_FAILURES=3
readonly BAZEL_EXITCODE_NO_TESTS_FOUND=4

# Save bazel worker logs.
# Common bazel codes fall into the single digit range. If a less common exit
# code happens, then we copy extra bazel logs.
if [[ $BAZEL_STATUS -gt 9 ]]; then
  copy_bazel_worker_logs
fi

if [[ " ${ARGV[@]} " =~ " --detect_flakes " ]];
then
  readonly SKIP_BAZEL_ARTIFACTS=1
fi
if [[ " ${ARGV[@]} " =~ " --very_flaky " ]];
then
  readonly IS_FLAKY_RUN=1
  readonly SKIP_BAZEL_ARTIFACTS=1
fi

# Artifacts should only be copied when the build succeeds. Test failures and no
# tests found are considered to be successful builds.
if [[ $BAZEL_STATUS -ne $BAZEL_EXITCODE_SUCCESS && \
      $BAZEL_STATUS -ne $BAZEL_EXITCODE_TEST_FAILURES && \
      $BAZEL_STATUS -ne $BAZEL_EXITCODE_NO_TESTS_FOUND ]];
then
  readonly SKIP_BAZEL_ARTIFACTS=1
fi

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then

  collect_logs
  if [[ $? -ne 0 ]]; then
    echo "Bazel logs-collector failed!"
    exit 1
  fi

  if [[ ! $SKIP_BAZEL_ARTIFACTS ]]; then
    copy_bazel_artifacts
    if [[ $? -ne 0 ]]; then
      echo "Failed to copy artifacts!"
      exit 1
    fi
  fi
fi

# It is OK if no tests are found when using --flaky.
if [[ $IS_FLAKY_RUN && $BAZEL_STATUS -eq $BAZEL_EXITCODE_NO_TESTS_FOUND  ]]; then
  exit 0
fi

# If the tests fail we report success, test results get displayed from other
# systems. b/192362688
if [[ $BAZEL_STATUS -eq $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $BAZEL_STATUS
fi
