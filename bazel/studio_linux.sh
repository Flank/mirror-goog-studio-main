#!/bin/bash -x
#
# Build and test a set of targets via bazel using RBE.

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
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

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

build_tag_filters=-no_linux
test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate

config_options="--config=dynamic"

# Verify test targets using the bazel 'flaky' attribute matches
# approved_flaky_targets.txt
QUERY_FLAKY_TESTS="${script_dir}/bazel query attr(flaky, 1, //tools/...)"
# Output approved flaky targets, excluding lines prefixed with '#'
APPROVED_FLAKY_TESTS="sed /^#.*/d ${script_dir}/approved_flaky_targets.txt"
UNAPPROVED_FLAKES=$(diff <($QUERY_FLAKY_TESTS | sort) <($APPROVED_FLAKY_TESTS) | grep '<')
if [[ $? -eq 0 ]];
then
  echo -e "Unapproved use of 'flaky' test attribute in the following targets:\n" \
    "$UNAPPROVED_FLAKES" \
    "Please contact android-devtools-infra@"
  exit 1
fi

# Generate a UUID for use as the bazel test invocation id
readonly invocation_id="$(uuidgen)"

# Temporary hack to switch to the "unb" rules:
mv "${script_dir}/project.bzl" "${script_dir}/project.bzl.bak"
echo "PROJECT = \"unb\"" > "${script_dir}/project.bzl"

test_failures=(
      -//tools/adt/idea/android/...
      -//tools/adt/idea/nav/safeargs/...
      -//tools/adt/idea/android-adb/...
      -//tools/adt/idea/android-common/...
      -//tools/adt/idea/android-debuggers/...
      -//tools/adt/idea/android-kotlin/...
      -//tools/adt/idea/android-lang-databinding/...
      -//tools/adt/idea/android-lang/...
      -//tools/adt/idea/android-layout-inspector/...
      -//tools/adt/idea/android-templates/...
      -//tools/adt/idea/assistant/...
      -//tools/adt/idea/build-attribution/...
      -//tools/adt/idea/compose-designer/...
      -//tools/adt/idea/connection-assistant/...
      -//tools/adt/idea/dagger/...
      -//tools/adt/idea/databinding/...
      -//tools/adt/idea/designer-perf-tests/...
      -//tools/adt/idea/designer/...
      -//tools/adt/idea/gradle-dsl/...
      -//tools/adt/idea/layout-inspector/...
      -//tools/adt/idea/layout-ui/...
      -//tools/adt/idea/lint/...
      -//tools/adt/idea/mlkit/...
      -//tools/adt/idea/native-symbolizer/...
      -//tools/adt/idea/nav/editor/...
      -//tools/adt/idea/old-agp-tests/...
      -//tools/adt/idea/profilers-android/...
      -//tools/adt/idea/profilers/...
      -//tools/adt/idea/profilers-ui/...
      -//tools/adt/idea/project-system-gradle-sync/...
      -//tools/adt/idea/project-system-gradle/...
      -//tools/adt/idea/resources-base/...
      -//tools/adt/idea/sdk-updates/...
      -//tools/adt/idea/smali/...
      -//tools/adt/idea/uitest-framework/...
      -//tools/adt/idea/whats-new-assistant/...
)

# Run Bazel
# --keep_going \
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  ${config_options} \
  --worker_max_instances=${WORKER_INSTANCES} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --define=meta_android_build_number="${BUILD_NUMBER}" \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=${script_name} \
  --embed_label="${BUILD_NUMBER}" \
  --profile="${DIST_DIR:-/tmp}/profile-${BUILD_NUMBER}.json.gz" \
  --runs_per_test=//tools/base/bazel:iml_to_build_consistency_test@2 \
  -- \
  //tools/adt/idea/studio:android-studio \
  //tools/adt/idea/studio:updater_deploy.jar \
  //tools/adt/idea/updater-ui:sdk-patcher.zip \
  //tools/adt/idea/native/installer:android-studio-bundle-data \
  //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector_deploy.jar \
  //tools/base/profiler/native/trace_processor_daemon \
  //tools/adt/idea/studio:test_studio \
  //tools/adt/idea/studio:searchable_options_test \
  //tools/... \
  //prebuilts/tools/... \
  //prebuilts/studio/... \
  ${test_failures[@]}

# $(< "${script_dir}/targets") contains:
  # @blaze//:aswb_tests \
  # //prebuilts/studio/... \
  # //prebuilts/tools/... \
  # //tools/... \
# Workaround: This invocation [ab]uses --runs_per_test to disable caching for the
# iml_to_build_consistency_test see https://github.com/bazelbuild/bazel/issues/6038
# This has the side effect of running it twice, but as it only takes a few seconds that seems ok.

mv "${script_dir}/project.bzl.bak" "${script_dir}/project.bzl"

readonly bazel_status=$?

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then

  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${DIST_DIR}"/upsalite_test_results.html

  readonly java="prebuilts/studio/jdk/linux/jre/bin/java"
  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  readonly bin_dir="$("${script_dir}"/bazel info ${config_options} bazel-bin)"

  if [[ $IS_POST_SUBMIT ]]; then
    readonly perfgate_arg="-perfzip \"${DIST_DIR}/perfgate_data.zip\""
  else
    readonly perfgate_arg=""
  fi

  ${java} -jar "${bin_dir}/tools/vendor/adt_infra_internal/rbe/logscollector/logs-collector_deploy.jar" \
    -bes "${DIST_DIR}/bazel-${BUILD_NUMBER}.bes" \
    -testlogs "${DIST_DIR}/logs/junit" \
    ${perfgate_arg}

  readonly artifacts_dir="${DIST_DIR}/artifacts"
  mkdir -p ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.linux.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.win.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/android-studio.mac.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/dynamic-layout-inspector/skiaparser.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/sdklib/commandlinetools_*.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/studio/updater_deploy.jar ${artifacts_dir}/android-studio-updater.jar
  cp -a ${bin_dir}/tools/adt/idea/updater-ui/sdk-patcher.zip ${artifacts_dir}
  cp -a ${bin_dir}/tools/adt/idea/native/installer/android-studio-bundle-data.zip ${artifacts_dir}
fi

BAZEL_EXITCODE_TEST_FAILURES=3

# For post-submit builds, if the tests fail we still want to report success
# otherwise ATP will think the build failed and there are no tests. b/152755167
if [[ $IS_POST_SUBMIT && $bazel_status == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $bazel_status
fi
