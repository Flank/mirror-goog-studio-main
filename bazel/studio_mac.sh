#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

if [[ $build_number =~ ^[0-9]+$ ]];
then
  IS_POST_SUBMIT=true
fi

declare -a conditional_flags
if [[ $IS_POST_SUBMIT ]]; then
  conditional_flags+=(--nocache_test_results)
  # Let CI builds populate the remote cache for mac builds.
  conditional_flags+=(--remote_upload_local_results=true)
fi

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

# Invocation ID must be lower case in Upsalite URL
readonly invocation_id=$(uuidgen | tr A-F a-f)

readonly config_options="--config=local --config=rcache"

"${script_dir}/bazel" \
        --max_idle_secs=60 \
        test \
        --keep_going \
        ${config_options} \
        --invocation_id=${invocation_id} \
        --build_tag_filters=-no_mac \
        --build_event_binary_file="${dist_dir}/bazel-${build_number}.bes" \
        --test_tag_filters=-no_mac,-no_test_mac,-ui_test,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate \
        --tool_tag=${script_name} \
        --worker_quit_after_build \
        --define=meta_android_build_number=${build_number} \
        --profile=${dist_dir}/profile-${build_number}.json.gz \
        "${conditional_flags[@]}" \
        -- \
        //tools/... \
        //tools/base/profiler/native/trace_processor_daemon \
        -//tools/base/build-system/integration-test/... \
        -//tools/adt/idea/android-lang:intellij.android.lang.tests_tests \
        -//tools/adt/idea/profilers-ui:intellij.android.profilers.ui_tests \
        -//tools/base/build-system/builder:tests.test

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  # info breaks if we pass --config=local or --config=rcache because they don't
  # affect info, so we need to pass only --config=release here in order to fetch the proper
  # binaries
  readonly bin_dir="$("${script_dir}"/bazel info --config=release bazel-bin)"
  cp -a ${bin_dir}/tools/base/dynamic-layout-inspector/skia/skiaparser.zip ${dist_dir}
  cp -a ${bin_dir}/tools/base/profiler/native/trace_processor_daemon/trace_processor_daemon ${dist_dir}
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  "${script_dir}/bazel" \
    run //tools/vendor/adt_infra_internal/rbe/logscollector:logs-collector \
    -- \
    -bes "${dist_dir}/bazel-${build_number}.bes" \
    -testlogs "${dist_dir}/logs/junit"

  if [[ $? -ne 0 ]]; then
    echo "Bazel logs-collector failed!"
    exit 1
  fi
fi

BAZEL_EXITCODE_TEST_FAILURES=3

# For post-submit builds, if the tests fail we still want to report success
# otherwise ATP will think the build failed and there are no tests. b/152755167
if [[ $IS_POST_SUBMIT && $bazel_status == $BAZEL_EXITCODE_TEST_FAILURES ]]; then
  exit 0
else
  exit $bazel_status
fi
