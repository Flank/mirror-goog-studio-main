#!/bin/bash -ex
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

# For Windows, we know that the string will contain Cygwin (if
# executed through Cygwin) combined with NT Version.  Since NT
# version is variable due to updates, check for CYGWIN in regex string.
if [[ "$unamestr" == *'CYGWIN'* ]]; then
  echo "Windows Cygwin Build Detected.  No Build to perform."
  exit 0;
fi

readonly script_dir="$(dirname "$0")"
"${script_dir}/bazel" --max_idle_secs=60 --bazelrc=/dev/null test --keep_going --nobuild_runfile_links --bes_backend=buildeventservice.googleapis.com --auth_credentials="$HOME"/.android-studio-alphasource.json --auth_scope=https://www.googleapis.com/auth/cloud-source-tools --project_id=908081808034 --config=remote --cache_test_results=no --build_tag_filters=-no_linux --test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable,-slow -- $(< "${script_dir}/targets")

if [[ -d "${dist_dir}" ]]; then
  # follow conventions to use gtest-testlog-forwarding on ATP
  readonly testlogs_dir="$(${script_dir}/bazel info -c opt bazel-testlogs)"
  mkdir "${dist_dir}"/gtest
  # This does not handle spaces in file names.
  for source_xml in $(cd "${testlogs_dir}" && find -name '*.xml' -printf '%P\n'); do
    target_xml="$(echo "${source_xml}" | tr '/' '_')"
    cp -pv "${testlogs_dir}/${source_xml}" "${dist_dir}/gtest/${target_xml}"
    # GTestXmlResultParser requires the testsuites element to have tests and time attributes.
    sed -i 's/<testsuites>/<testsuites tests="0" time="0">/' "${dist_dir}/gtest/${target_xml}"
  done
fi
