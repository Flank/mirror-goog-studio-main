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

readonly CONFIG_OPTIONS="--config=dynamic --config=datasize_aspect"

####################################
# Copies bazel artifacts to an output directory named 'artifacts'.
# Globals:
#   DIST_DIR
#   SCRIPT_DIR
#   CONFIG_OPTIONS
# Arguments:
#   None
####################################
function copy_bazel_artifacts() {
  local -r artifacts_dir="${DIST_DIR}/artifacts"
  mkdir -p ${artifacts_dir}
  local -r bin_dir="$("${SCRIPT_DIR}"/bazel info ${CONFIG_OPTIONS} bazel-bin)"

  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.linux.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.win.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.mac.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.mac_arm.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/updater_deploy.jar ${artifacts_dir}/android-studio-updater.jar
  cp -a ${bin_dir}/tools/adt/idea/updater-ui/sdk-patcher.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/native/installer/android-studio-bundle-data.zip ${artifacts_dir}

  cp -a ${bin_dir}/tools/base/dynamic-layout-inspector/skia/skiaparser.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/sdklib/commandlinetools_*.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/ddmlib/libtools.ddmlib.jar ${artifacts_dir}/ddmlib.jar
  cp -a ${bin_dir}/tools/base/ddmlib/libincfs.jar ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon ${artifacts_dir}
  cp -a ${bin_dir}/tools/vendor/google/game-tools/packaging/game-tools-linux.tar.gz ${artifacts_dir}
  cp -a ${bin_dir}/tools/vendor/google/game-tools/packaging/game-tools-win.zip ${artifacts_dir}
}

####################################
# Download a flake retry bazelrc file from GCS.
# Arguments:
#   1. File basename on GCS
#   2. Destination directory
# Echoes:
#   If successful, the local path; Otherwise, nothing
####################################
function download_flake_retry_rc() {
  local -r gcs_path="gs://adt-byob/known-flakes/studio-linux/${1}"
  mkdir -p $2
  gsutil cp $gcs_path "${2}/${1}"
  if [[ $? -eq 0 ]]; then
    echo "${2}/${1}"
  fi
}

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
    test_tag_filters=-no_linux,-no_test_linux,very_flaky
  elif [[ $BUILD_TYPE == "POSTSUBMIT" ]]; then
    conditional_flags+=(--nocache_test_results)
    conditional_flags+=(--flaky_test_attempts=2)
  fi

  # Generate a UUID for use as the bazel test invocation id
  local -r invocation_id="$(uuidgen)"

  if [[ -d "${DIST_DIR}" ]]; then
    # Generate a simple html page that redirects to the test results page.
    echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${DIST_DIR}"/upsalite_test_results.html
  fi

  # Workaround: This invocation [ab]uses --runs_per_test to disable caching for the
  # iml_to_build_consistency_test see https://github.com/bazelbuild/bazel/issues/6038
  # This has the side effect of running it twice, but as it only takes a few seconds that seems ok.
  local -r extra_test_flags=(--runs_per_test=//tools/base/bazel:iml_to_build_consistency_test@2)
  declare -a bazelrc_flags
  # For presubmit builds, try download bazelrc to deflake builds
  if [[ $BUILD_TYPE == "PRESUBMIT" && -d "${DIST_DIR}" ]]; then
    local bazelrc=$(download_flake_retry_rc "auto-retry.bazelrc" "${DIST_DIR}/flake-retry")
    if [[ $bazelrc ]]; then bazelrc_flags+=("--bazelrc=${bazelrc}"); fi
  fi

  # Run Bazel
  "${SCRIPT_DIR}/bazel" \
    --max_idle_secs=60 \
    "${bazelrc_flags[@]}" \
    test \
    --keep_going \
    ${CONFIG_OPTIONS} \
    --worker_max_instances=${worker_instances} \
    --invocation_id=${invocation_id} \
    --build_tag_filters=${build_tag_filters} \
    --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
    --define=meta_android_build_number="${BUILD_NUMBER}" \
    --test_tag_filters=${test_tag_filters} \
    --tool_tag=${SCRIPT_NAME} \
    --embed_label="${AS_BUILD_NUMBER}" \
    --profile="${DIST_DIR:-/tmp}/profile-${BUILD_NUMBER}.json.gz" \
    "${extra_test_flags[@]}" \
    "${conditional_flags[@]}" \
    -- \
    //tools/adt/idea/studio:android-studio \
    //tools/adt/idea/studio:updater_deploy.jar \
    //tools/adt/idea/updater-ui:sdk-patcher.zip \
    //tools/adt/idea/native/installer:android-studio-bundle-data \
    //tools/base/profiler/native/trace_processor_daemon \
    //tools/adt/idea/studio:test_studio \
    //tools/vendor/google/game-tools/packaging:packaging-linux \
    //tools/vendor/google/game-tools/packaging:packaging-win \
    //tools/base/ddmlib:tools.ddmlib \
    //tools/base/ddmlib:incfs \
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

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then

  collect_logs
  if [[ $? -ne 0 ]]; then
    echo "Bazel logs-collector failed!"
    exit 1
  fi

  if [[ ! $SKIP_BAZEL_ARTIFACTS ]]; then
    copy_bazel_artifacts
  fi
fi

readonly BAZEL_EXITCODE_TEST_FAILURES=3
readonly BAZEL_EXITCODE_NO_TESTS_FOUND=4

# It is OK if no tests are found when using --flaky.
if [[ $IS_FLAKY_RUN && $BAZEL_STATUS -eq $BAZEL_EXITCODE_NO_TESTS_FOUND  ]]; then
  exit 0
fi

# For post-submit builds, if the tests fail we still want to report success
# otherwise ATP will think the build failed and there are no tests. b/152755167
if [[ $BUILD_TYPE == "POSTSUBMIT" && $BAZEL_STATUS -eq $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $BAZEL_STATUS
fi
