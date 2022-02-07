#!/bin/bash

set -e
trap 'echo "ERROR: maven_fetch.sh failed" >&2' ERR

# The directory containing this script file.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd -P)"

${SCRIPT_DIR}/../bazel run //tools/base/bazel:local_maven_repository_generator

echo "Done."
echo "Note: Do not forget to commit downloaded artifacts and/or changes to the BUILD.maven file."
