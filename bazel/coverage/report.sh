#!/bin/bash

readonly report_name="$1"
readonly html_dir="$2"

readonly usage="report.sh <name of coverage report> [<output directory for html report>]"

if [[ -z ${report_name} ]]; then
  echo ${usage}
elif [[ ! -d ${html_dir} && ! -z ${html_dir} ]]; then
  echo ${usage}
fi

bazel test --define agent_coverage=true --config=remote -- "@cov//:${report_name}.suite" || exit $?
bazel build --config=remote -- "@cov//:${report_name}.lcov" || exit $?
if [[ -d ${html_dir} ]]; then
  echo "Generating HTML report in ${html_dir}"
  genhtml -o ${html_dir} -p $(pwd) --no-function-coverage "bazel-genfiles/external/cov/${report_name}/lcov" || exit $?
fi
echo "Done"
