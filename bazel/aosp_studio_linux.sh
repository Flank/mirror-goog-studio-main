#!/bin/bash -x
#
# Build the targets that we support building on AOSP

readonly ARGV=("$@")

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
readonly BUILD_NUMBER="${BUILD_NUMBER:-SNAPSHOT}"
# AS_BUILD_NUMBER is the same as BUILD_NUMBER but omits the P for presubmit,
# to satisfy Integer.parseInt in BuildNumber.parseBuildNumber
# The "#P" matches "P" only at the beginning of BUILD_NUMBER
readonly AS_BUILD_NUMBER="${BUILD_NUMBER/#P/0}"

readonly SCRIPT_DIR="$(dirname "$0")"
readonly SCRIPT_NAME="$(basename "$0")"

readonly CONFIG_OPTIONS="--config=ci --config=without_vendor"

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

  cp -a ${bin_dir}/tools/base/gmaven/gmaven_without_vendor.zip ${artifacts_dir}
)}

####################################
# Generates flag values and runs bazel test.
# Globals:
#   ARGV
#   AS_BUILD_NUMBER
#   BUILD_NUMBER
#   CONFIG_OPTIONS
#   DIST_DIR
#   SCRIPT_DIR
#   SCRIPT_NAME
# Arguments:
#   None
####################################
function run_bazel_build() {
  local build_tag_filters=-no_linux

  # Generate a UUID for use as the bazel test invocation id
  local -r invocation_id="$(uuidgen)"

  if [[ -d "${DIST_DIR}" ]]; then
    # Generate a simple html page that redirects to the test results page.
    echo "<head><meta http-equiv=\"refresh\" content=\"0; URL='https://fusion2.corp.google.com/invocations/${invocation_id}'\" /></head>" > "${DIST_DIR}"/upsalite_test_results.html
  fi

  # Run Bazel
  "${SCRIPT_DIR}/bazel" \
    --max_idle_secs=60 \
    "${bazelrc_flags[@]}" \
    build \
    ${CONFIG_OPTIONS} \
    --invocation_id=${invocation_id} \
    --build_tag_filters=${build_tag_filters} \
    --define=meta_android_build_number="${BUILD_NUMBER}" \
    --build_metadata=ANDROID_BUILD_ID="${BUILD_NUMBER}" \
    --tool_tag=${SCRIPT_NAME} \
    --embed_label="${AS_BUILD_NUMBER}" \
    -- \
    //tools/base/gmaven:gmaven_without_vendor.zip
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

run_bazel_build
readonly BAZEL_STATUS=$?

readonly BAZEL_EXITCODE_SUCCESS=0

# Save bazel worker logs.
# Common bazel codes fall into the single digit range. If a less common exit
# code happens, then we copy extra bazel logs.
if [[ $BAZEL_STATUS -gt 9 ]]; then
  copy_bazel_worker_logs
fi

# Artifacts should only be copied when the build succeeds. Test failures and no
# tests found are considered to be successful builds.
if [[ $BAZEL_STATUS -ne $BAZEL_EXITCODE_SUCCESS ]];
then
  readonly SKIP_BAZEL_ARTIFACTS=1
fi

# http://g3doc/wireless/android/build_tools/g3doc/public/buildbot#environment-variables
if [[ -d "${DIST_DIR}" ]]; then

  if [[ ! $SKIP_BAZEL_ARTIFACTS ]]; then
    copy_bazel_artifacts
    if [[ $? -ne 0 ]]; then
      echo "Failed to copy artifacts!"
      exit 1
    fi
  fi
fi

exit $BAZEL_STATUS
