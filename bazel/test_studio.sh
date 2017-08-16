#!/bin/bash -ex
# Invoked by Android Build Launchcontrol for continuous builds.

# Expected arguments:
readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(dirname "$0")"
"${script_dir}/bazel" test --test_tag_filters=-gradle_integration --test_output=errors --test_summary=detailed $(< "${script_dir}/targets")

readonly testlogs_dir="$(${script_dir}/bazel info bazel-testlogs)"
(cd "${testlogs_dir}" && zip -qr "${dist_dir}/bazel_testlogs_${build_number}.zip" *)
