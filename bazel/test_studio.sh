#!/bin/bash -x
#
# Build and test a set of targets via bazel using RBE.
# This script is invoked by Android Build Launchcontrol, e.g:
#  $ tools/base/bazel/test_studio.sh \
#        out \
#        /buildbot/dist_dirs/git_studio-master-dev-linux-studio_rbe/P9370467/0 \
#        P9370467

readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
readonly script_name="$(basename "$0")"

build_tag_filters=-no_linux
test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate_only

config_options="--config=remote"

# Generate a UUID for use as the bazel invocation id
readonly invocation_id="$(uuidgen)"

# Run Bazel
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --invocation_id=${invocation_id} \
  --build_tag_filters=${build_tag_filters} \
  --define=meta_android_build_number=${build_number} \
  --test_tag_filters=${test_tag_filters} \
  --tool_tag=${script_name} \
  --profile=${dist_dir}/profile-${build_number}.json \
  -- \
  //tools/idea/updater:updater_deploy.jar \
  $(< "${script_dir}/targets")

readonly bazel_status=$?

# The dist_dir always exists on builds triggered by go/ab, but may not
# exist during local testing
if [[ -d "${dist_dir}" ]]; then

  # Generate a simple html page that redirects to the test results page.
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${invocation_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  readonly bin_dir="$("${script_dir}"/bazel info ${config_options} bazel-bin)"
  cp -a ${bin_dir}/tools/idea/updater/updater_deploy.jar ${dist_dir}/android-studio-updater.jar

  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  mkdir "${dist_dir}"/bazel-testlogs
  (cd "${testlogs_dir}" && zip -R "${dist_dir}"/bazel-testlogs/xml_files.zip "*.xml")

fi

exit $bazel_status
