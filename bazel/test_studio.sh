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

build_tag_filters=-no_linux
test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable,-perfgate_only

config_options="--config=remote"

# Grab the location of the command_log file for bazel daemon so we can search it later.
readonly command_log="$("${script_dir}"/bazel info ${config_options} command_log)"

# Run Bazel
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --keep_going \
  ${config_options} \
  --build_tag_filters=${build_tag_filters} \
  --test_tag_filters=${test_tag_filters} \
  --profile=${dist_dir}/prof \
  -- \
  //tools/idea/updater:updater_deploy.jar \
  $(< "${script_dir}/targets")

readonly bazel_status=$?

# The dist_dir always exists on builds triggered by go/ab, but may not
# exist during local testing
if [[ -d "${dist_dir}" ]]; then

  # Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
  readonly upsalite_id="$(sed -n 's/\r$//;s/^.* invocation_id: //p' "${command_log}")"
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  readonly bin_dir="$("${script_dir}"/bazel info ${config_options} bazel-bin)"
  cp -a ${bin_dir}/tools/idea/updater/updater_deploy.jar ${dist_dir}/android-studio-updater.jar

  readonly testlogs_dir="$("${script_dir}/bazel" info bazel-testlogs ${config_options})"
  mkdir "${dist_dir}"/bazel-testlogs
  (cd "${testlogs_dir}" && zip -R "${dist_dir}"/bazel-testlogs/xml_files.zip "*.xml")

  # Create profile html in ${dist_dir} so it ends up in Artifacts.
  ${script_dir}/bazel analyze-profile --html ${dist_dir}/prof

fi

exit $bazel_status
