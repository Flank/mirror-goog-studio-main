#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

# We wish to set up a new Mac based build branch.  This initial check
# is to allow a simple echo statement confirmation on the buildbot.
unamestr=`uname`
if [[ "$unamestr" == 'Darwin' ]]; then
  echo "Mac Build Detected.  No Build to perform."
  exit 0;
fi

test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable

readonly script_dir="$(dirname "$0")"

# Grab the location of the command_log file for bazel daemon so we can search it later.
readonly command_log="$("${script_dir}"/bazel --bazelrc="${script_dir}"/toplevel.bazel.rc info --config=remote command_log)"

# If the build number starts with a 'P', this is a pre-submit builder.
if [[ ${build_number:0:1} == 'P' ]]; then
  test_tag_filters=${test_tag_filters},-no_psq
fi

# Run Bazel
"${script_dir}/bazel" --max_idle_secs=60 --bazelrc=${script_dir}/toplevel.bazel.rc test --keep_going --nobuild_runfile_links --bes_backend=buildeventservice.googleapis.com --auth_credentials="$HOME"/.android-studio-alphasource.json --auth_scope=https://www.googleapis.com/auth/cloud-source-tools --project_id=908081808034 --config=remote --cache_test_results=no --build_tag_filters=-no_linux --test_tag_filters=${test_tag_filters} -- $(< "${script_dir}/targets") //tools/base/bazel/foundry:test

readonly bazel_status=$?

if [[ -d "${dist_dir}" ]]; then
  # Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
  readonly upsalite_id="$(sed -n 's/\r$//;s/^.* invocation_id: //p' "${command_log}")"
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id}'\" />" > "${dist_dir}"/upsalite_test_results.html

  # follow conventions to use gtest-testlog-forwarding on ATP
  readonly testlogs_dir="$(${script_dir}/bazel --bazelrc=${script_dir}/toplevel.bazel.rc info bazel-testlogs --config=remote)"
  mkdir "${dist_dir}"/gtest
  # This does not handle spaces in file names.
  for source_xml in $(cd "${testlogs_dir}" && find -name '*.xml' -printf '%P\n'); do
    target_xml="$(echo "${source_xml}" | tr '/' '_')"
    cp -pv "${testlogs_dir}/${source_xml}" "${dist_dir}/gtest/${target_xml}"
    # GTestXmlResultParser requires the testsuites element to have tests and time attributes.
    sed -i 's/<testsuites>/<testsuites tests="0" time="0">/' "${dist_dir}/gtest/${target_xml}"
  done
fi

exit $bazel_status
