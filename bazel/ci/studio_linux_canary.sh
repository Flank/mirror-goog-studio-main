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
export USE_BAZEL_VERSION=last_rc

"${SCRIPT_DIR}/bazel" \
  --max_idle_secs=60 \
  test \
  --config=ci \
  --config=ants \
  --invocation_id=${INVOCATION_ID} \
  --build_tag_filters=${BUILD_TAG_FILTERS} \
  --build_event_binary_file="${DIST_DIR:-/tmp}/bazel-${BUILD_NUMBER}.bes" \
  --build_metadata=ab_build_id="${BUILD_NUMBER}" \
  --build_metadata=ab_target=studio-linux_canary \
  --define=meta_android_build_number="${BUILD_NUMBER}" \
  --tool_tag=studio-linux-canary \
  --test_tag_filters="-no_test_linux,-qa_smoke,-qa_fast,-qa_unreliable,-perfgate,-very_flaky" \
  --verbose_failures \
  -- \
  //tools/adt/idea/...

readonly BAZEL_STATUS=$?

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then
  # Generate a simple html page that redirects to the test results page.
  echo "<head><meta http-equiv=\"refresh\" content=\"0; url='http://sponge2/${INVOCATION_ID}'\" /></head>" > "${DIST_DIR}"/sponge_build_results.html
fi

exit $BAZEL_STATUS
