#!/bin/bash -ex
# Invoked by Android Build Launchcontrol for continuous builds.
SCRIPT_DIR="$(dirname "$0")"
"$SCRIPT_DIR/bazel" --batch test $(< "${SCRIPT_DIR}/targets")
