#!/bin/bash

set -e

# The directory containing this script file.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd -P)"

BAZEL="${SCRIPT_DIR}/../bazel"

# Use $RANDOM to make sure bazel never uses a cached result for the
# repository_rule, and thus, always executes the generator.
# Use "fetch" instead of "build" to avoid a race (root cause yet
# unknown) between fetching and the glob() for artifacts.
MAVEN_FETCH=$RANDOM "$BAZEL" fetch @maven//...

