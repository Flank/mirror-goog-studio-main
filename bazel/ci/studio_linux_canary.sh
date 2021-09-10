#!/bin/bash -x
#
# This script runs 'bazel build' as a canary check to validate the continuous
# integreation system is configurated properly and builds successfully.

readonly BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"
readonly BUILD_TAG_FILTERS=-no_linux
readonly SCRIPT_DIR="$(dirname "$0")/.."
readonly INVOCATION_ID="$(uuidgen)"

# Build everything, except the final studio artifacts. Building everything
# under //tools/... matches what studio_win_canary builds, and makes sure
# canary builds will have something to build in most cases (not constantly
# getting cached hits). We exclude studio artifacts because they rely on
# the --embed_label value in the non-canary build. This reduces the
# effectiveness of building and caching the studio artifacts. Since the
# final artifacts are quite large (1GB), are generated for all platforms
# (linux, windows, mac, mac_arm), and the cache cannot be shared, we exclude
# them.
readonly BUILD_TARGETS="//tools/... -//tools/adt/idea/studio/..."

"${SCRIPT_DIR}/bazel" \
  --max_idle_secs=60 \
  build \
  --config=dynamic \
  --invocation_id=${INVOCATION_ID} \
  --build_tag_filters=${BUILD_TAG_FILTERS} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --define=meta_android_build_number="${BUILD_NUMBER}" \
  --tool_tag=studio-linux-canary \
  -- \
  ${BUILD_TARGETS}

readonly BAZEL_STATUS=$?

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then
  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; url='https://source.cloud.google.com/results/invocations/${INVOCATION_ID}'\" />" > "${DIST_DIR}"/upsalite_build_results.html
fi

exit $BAZEL_STATUS
