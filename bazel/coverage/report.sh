#!/bin/bash

readonly report_name="$1"
readonly html_dir="$2"

if [[ ! -f "./tools/base/bazel/coverage/report.sh" ]]; then
  echo "you should run this from the top level WORKSPACE directory"
  exit 1
fi

readonly genhtml="$(which genhtml)"
if [[ -z ${genhtml} ]]; then
  echo "you need genhtml to make reports. run this: sudo apt install lcov"
  exit 1
fi

readonly usage="report.sh <name of coverage report> <output directory for html report>"

if [[ -z ${report_name} || -z ${html_dir} ]]; then
  echo ${usage}
  exit 1
fi

echo "Run tests to generate coverage data"
bazel test --define agent_coverage=true --config=remote -- "@cov//:${report_name}.suite" || exit $?
echo "Processing raw coverage data"
bazel build --config=remote -- "@cov//:${report_name}.lcov" || exit $?
echo "Generating HTML report in ${html_dir}"
genhtml -o ${html_dir} -p $(pwd) --no-function-coverage "bazel-genfiles/external/cov/${report_name}/lcov" || exit $?
echo "Done"
