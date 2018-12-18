#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.

readonly dist_dir="$1"

readonly script_dir="$(dirname "$0")"

# Grab the location of the command_log file for bazel daemon so we can search it later.
readonly command_log="$("${script_dir}"/bazel info --config=remote command_log)"

# Run Bazel with coverage instrumentation
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  --config=remote \
  --auth_credentials="$HOME"/.android-studio-alphasource.json \
  --test_tag_filters=-no_linux,-no_test_linux \
  --define agent_coverage=true \
  -- \
  //tools/... \
  -//tools/adt/idea/android-uitests/... \
  -//tools/adt/idea/uitest-framework:intellij.android.guiTestFramework_tests \
  -//tools/base/perf-logger:studio.perf-logger_tests \
  || exit $?

# Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
readonly upsalite_id="$(sed -n 's/\r$//;s/^.* invocation_id: //p' "${command_log}")"

# Create a temp file to pass production targets to report generator
readonly production_targets_file=$(mktemp)

# Collect the production targets
readonly universe="//tools/... - //tools/adt/idea/android-uitests/..."

"${script_dir}/bazel" \
  query \
  "kind(test, rdeps(${universe}, deps(//tools/base:coverage_report)))" \
  | tee $production_targets_file \
  || exit $?

# Generate the Jacoco report
readonly testlogs_dir="$(${script_dir}/bazel info bazel-testlogs --config=remote)"

"${script_dir}/bazel" \
  run \
  //tools/base:coverage_report \
  --config=remote \
  --auth_credentials="$HOME"/.android-studio-alphasource.json \
  -- \
  tools/base/coverage_report \
  $production_targets_file \
  $testlogs_dir \
  || exit $?

# Resolve to sourcefiles and convert to LCOV
python "${script_dir}/jacoco_to_lcov.py" || exit $?

if [[ -d "${dist_dir}" ]]; then
  # Copy the report to ab/ outputs
  mkdir "${dist_dir}/coverage"
  cp -pv "./out/lcov" "${dist_dir}/coverage"
  cp -pv "./out/worst" "${dist_dir}/coverage"
  cp -pv "./out/worstNoFiles" "${dist_dir}/coverage"
  cp -pv "./out/missing" "${dist_dir}/coverage"
  cp -pv "./out/fake" "${dist_dir}/coverage"

  # Link to test results
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
fi

exit 0
