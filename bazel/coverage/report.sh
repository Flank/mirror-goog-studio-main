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

echo "Delete old baseline coverage file lists"
find bazel-bin/ -name '*.coverage.baseline*' | xargs rm -fv || exit $?
echo "Generate baseline coverage file lists"
bazel build --config=rcache --build_tag_filters="coverage-sources" -- //tools/... || exit $?
echo "Run tests to generate coverage data"
bazel test --define agent_coverage=true --config=dynamic -- "@cov//:${report_name}.suite" @baseline//... || exit $?
echo "Processing raw coverage data"
bazel build --config=rcache -- "@cov//:${report_name}.lcov.notests" || exit $?
echo "Generating HTML report in ${html_dir}"
genhtml -o ${html_dir} -p $(pwd) --no-function-coverage "bazel-bin/external/cov/${report_name}/lcov.notests" || exit $?
echo "Done"
