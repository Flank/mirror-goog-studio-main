#!/bin/bash -x
#
# Build and test a set of targets via bazel using RBE.

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

build_tag_filters=-no_linux
test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate_only

config_options="--config=remote"

# Enable "Builds without Bytes" which reduces downloading of all action outputs
# These settings are made here rather than in the bazelrc files:
#   - many of our linux invocations (coverage, qa, perf, ..) assume that logs
#     will be available locally, which isn't true with these flags
#   - This doesn't work on Windows as yet (b/139655502)
readonly MINIMAL_DOWNLOADS="--experimental_inmemory_jdeps_files --experimental_inmemory_dotd_files --experimental_remote_download_outputs=minimal"

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

# Run Bazel
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  ${MINIMAL_DOWNLOADS} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --define=meta_android_build_number="${BUILD_NUMBER}" \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=${script_name} \
  --profile="${DIST_DIR:-/tmp}/profile-${BUILD_NUMBER}.json" \
  -- \
  //tools/idea/updater:updater_deploy.jar \
  $(< "${script_dir}/targets")

readonly bazel_status=$?

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then

  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${DIST_DIR}"/upsalite_test_results.html

  readonly bin_dir="$("${script_dir}"/bazel info ${config_options} bazel-bin)"
  cp -a ${bin_dir}/tools/idea/updater/updater_deploy.jar ${DIST_DIR}/android-studio-updater.jar

  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  mkdir "${DIST_DIR}"/bazel-testlogs
  (cd "${testlogs_dir}" && zip -R "${DIST_DIR}"/bazel-testlogs/xml_files.zip "*.xml")

fi

exit $bazel_status
