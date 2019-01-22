#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"

# Grab the location of the command_log file for bazel daemon so we can search it later.
readonly command_log="$(${script_dir}/bazel info command_log --config=postsubmit --config=mac-experimental --auth_credentials=/buildbot/android-studio-alphasource.json)"

# Run Bazel
"${script_dir}/bazel" --max_idle_secs=60 test --config=postsubmit --config=local --config=mac-experimental --build_tag_filters=-no_mac --test_tag_filters=-no_mac,-no_test_mac,-qa_sanity,-qa_fast,-qa_unreliable --auth_credentials=/buildbot/android-studio-alphasource.json --profile=${dist_dir}/prof -- $(< "${script_dir}/targets")

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  # Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
  readonly upsalite_id=`sed -n -e 's/.*invocation_id: //p' ${command_log}`
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  # Create profile html in ${dist_dir} so it ends up in Artifacts.
  ${script_dir}/bazel analyze-profile --html ${dist_dir}/prof
fi

exit $bazel_status
