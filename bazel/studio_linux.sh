#!/bin/bash -x
#
# Build and test a set of targets via bazel using RBE.

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
# AS_BUILD_NUMBER is the same as BUILD_NUMBER but omits the P for presubmit
AS_BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"
AS_BUILD_NUMBER="${BUILD_NUMBER/P/0}"  # for AB presubmit: satisfy Integer.parseInt in BuildNumber.parseBuildNumber
BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"

if [[ $BUILD_NUMBER != SNAPSHOT ]];
then
  WORKER_INSTANCES=auto
else
  # Assuming manual user invocation and using limited host resources.
  # This should prevent bazel from causing the host to freeze due to using
  # too much memory.
  WORKER_INSTANCES=2
fi

if [[ $BUILD_NUMBER =~ ^[0-9]+$ ]];
then
  IS_POST_SUBMIT=true
fi

build_tag_filters=-no_linux
test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate,-very_flaky

declare -a conditional_flags
if [[ " $@ " =~ " --detect_flakes " ]];
then
  conditional_flags+=(--runs_per_test=20)
  conditional_flags+=(--runs_per_test_detects_flakes)
  conditional_flags+=(--nocache_test_results)

# Only run tests tagged with `very_flaky`, this is different than tests using
# the 'Flaky' attribute/tag. Tests that are excessively flaky use this tag to
# avoid running in presubmit.
elif [[ " $@ " =~ " --very_flaky " ]];
then
  is_flaky_run=1
  skip_bazel_artifacts=1
  conditional_flags+=(--build_tests_only)
  test_tag_filters=-no_linux,-no_test_linux,very_flaky

elif [[ $IS_POST_SUBMIT ]]; then
  conditional_flags+=(--nocache_test_results)
  conditional_flags+=(--flaky_test_attempts=2)
fi

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

config_options="--config=dynamic"

# Generate a UUID for use as the bazel test invocation id
readonly invocation_id="$(uuidgen)"

# Run Bazel
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --remote_download_minimal \
  --worker_max_instances=${WORKER_INSTANCES} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --define=meta_android_build_number="${BUILD_NUMBER}" \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=${script_name} \
  --embed_label="${AS_BUILD_NUMBER}" \
  --profile="${DIST_DIR:-/tmp}/profile-${BUILD_NUMBER}.json.gz" \
  --runs_per_test=//tools/base/bazel:iml_to_build_consistency_test@2 \
  "${conditional_flags[@]}" \
  -- \
  //tools/adt/idea/studio:android-studio \
  //tools/adt/idea/studio:updater_deploy.jar \
  //tools/adt/idea/updater-ui:sdk-patcher.zip \
  //tools/adt/idea/native/installer:android-studio-bundle-data \
  //tools/base/profiler/native/trace_processor_daemon \
  //tools/adt/idea/studio:test_studio \
  //tools/adt/idea/studio:searchable_options_test \
  //tools/vendor/google/game-tools/packaging:packaging-linux \
  //tools/vendor/google/game-tools/packaging:packaging-win \
  $(< "${script_dir}/targets")
# Workaround: This invocation [ab]uses --runs_per_test to disable caching for the
# iml_to_build_consistency_test see https://github.com/bazelbuild/bazel/issues/6038
# This has the side effect of running it twice, but as it only takes a few seconds that seems ok.
readonly bazel_status=$?

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then

  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${DIST_DIR}"/upsalite_test_results.html

  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  readonly bin_dir="$("${script_dir}"/bazel info ${config_options} bazel-bin)"

  if [[ $IS_POST_SUBMIT ]]; then
    readonly perfgate_arg="-perfzip \"${DIST_DIR}/perfgate_data.zip\""
  else
    readonly perfgate_arg=""
  fi

  declare -a download_arg
  if [[ ! $skip_bazel_artifacts ]]; then
    readonly artifacts_dir="${DIST_DIR}/artifacts"
    mkdir -p ${artifacts_dir}

    download_arg+=(-download tools/adt/idea/studio/android-studio.linux.zip ${artifacts_dir})
    download_arg+=(-download tools/adt/idea/studio/android-studio.win.zip ${artifacts_dir})
    download_arg+=(-download tools/adt/idea/studio/android-studio.mac.zip ${artifacts_dir})
    download_arg+=(-download tools/adt/idea/studio/updater_deploy.jar ${artifacts_dir})
    download_arg+=(-download tools/adt/idea/updater-ui/sdk-patcher.zip ${artifacts_dir})
    download_arg+=(-download tools/adt/idea/native/installer/android-studio-bundle-data.zip ${artifacts_dir})
    download_arg+=(-download tools/base/dynamic-layout-inspector/skiaparser.zip ${artifacts_dir})
    download_arg+=(-download tools/base/sdklib/commandlinetools_linux.zip ${artifacts_dir})
    download_arg+=(-download tools/base/sdklib/commandlinetools_win.zip ${artifacts_dir})
    download_arg+=(-download tools/base/sdklib/commandlinetools_mac.zip ${artifacts_dir})
    download_arg+=(-download tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon ${artifacts_dir})
    download_arg+=(-download tools/vendor/google/game-tools/packaging/game-tools-linux.tar.gz ${artifacts_dir})
    download_arg+=(-download tools/vendor/google/game-tools/packaging/game-tools-win.zip ${artifacts_dir})
  fi

  "${script_dir}/bazel" \
    run //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector \
    ${config_options} \
    -- \
    -bes "${DIST_DIR}/bazel-${BUILD_NUMBER}.bes" \
    -testlogs "${DIST_DIR}/logs/junit" \
    ${perfgate_arg} \
    ${download_arg[@]}
fi

BAZEL_EXITCODE_TEST_FAILURES=3
BAZEL_EXITCODE_NO_TESTS_FOUND=4

# It is OK if no tests are found when using --flaky.
if [[ $is_flaky_run && $bazel_status == $BAZEL_EXITCODE_NO_TESTS_FOUND  ]]; then
  exit 0
fi

# For post-submit builds, if the tests fail we still want to report success
# otherwise ATP will think the build failed and there are no tests. b/152755167
if [[ $IS_POST_SUBMIT && $bazel_status == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $bazel_status
fi
