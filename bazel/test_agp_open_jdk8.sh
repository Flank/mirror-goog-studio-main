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

# Generate a UUID for use as the bazel test invocation id
readonly invocation_id="$(uuidgen)"

# Run Bazel for tests under //tools/base/build-system/integration-test/ using OpenJdk8
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --host_javabase=//prebuilts/studio/jdk:jdk_runtime \
  --javabase=//prebuilts/studio/jdk:jdk_runtime \
  --host_java_toolchain=@bazel_tools//tools/jdk:toolchain_hostjdk8 \
  --java_toolchain=@bazel_tools//tools/jdk:toolchain_hostjdk8 \
  --keep_going \
  ${config_options} \
  --worker_max_instances=${WORKER_INSTANCES} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --define=meta_android_build_number="${BUILD_NUMBER}" \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=${script_name} \
  --profile="${DIST_DIR:-/tmp}/profile-${BUILD_NUMBER}.json.gz" \
  -- \
  //tools/base/build-system/integration-test/...

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
fi

BAZEL_EXITCODE_TEST_FAILURES=3

# For post-submit builds, if the tests fail we still want to report success
# otherwise ATP will think the build failed and there are no tests. b/152755167
if [[ $IS_POST_SUBMIT && $bazel_status == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $bazel_status
fi

