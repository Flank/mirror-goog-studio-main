#!/bin/bash -ex
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
"${script_dir}/bazel" --max_idle_secs=60 --bazelrc=/dev/null test --keep_going --bes_backend=buildeventservice.googleapis.com --auth_credentials="$HOME"/.android-studio-alphasource.json --auth_scope=https://www.googleapis.com/auth/cloud-source-tools --project_id=908081808034 --config=remote --cache_test_results=no --build_tag_filters=-no_linux --test_tag_filters=-no_linux,-no_test_linux,-qa_sanity,-qa_fast,-qa_unreliable $(< "${script_dir}/targets")

readonly testlogs_dir="$(${script_dir}/bazel info bazel-testlogs)"
(cd "${testlogs_dir}" && zip -qr "${dist_dir}/bazel_testlogs_${build_number}.zip" *)
