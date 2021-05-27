#!/bin/bash -x
#
# This script runs 'bazel build' as a canary check to validate the continuous
# integreation system is configurated properly and builds successfully.

BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"
build_tag_filters=-no_linux
readonly script_dir="$(dirname "$0")/.."
readonly invocation_id="$(uuidgen)"

# Build everything, except the final studio artifacts. Building everything
# under //tools/... matches what studio_win_canary builds, and makes sure
# canary builds will have something to build in most cases (not constantly
# getting cached hits). We exclude studio artifacts because they rely on
# the --embed_label value in the non-canary build. This reduces the
# effectiveness of building and caching the studio artifacts. Since the
# final artifacts are quite large (1GB), are generated for all platforms
# (linux, windows, mac, mac_arm), and the cache cannot be shared, we exclude
# them.
readonly build_targets="//tools/... -//tools/adt/idea/studio/..."

"${script_dir}/bazel" \
  --max_idle_secs=60 \
  build --config=dynamic \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --define=meta_android_build_number="${BUILD_NUMBER}" \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=studio-linux-canary \
  -- \
  ${build_targets}

readonly bazel_status=$?

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then
  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${DIST_DIR}"/upsalite_test_results.html
fi

exit $bazel_status
