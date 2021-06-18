#!/usr/bin/env bash
set -eu

[[ $# -eq 2 ]] || { echo "Usage: $0 <directory> <kotlin-version>"; exit 1; }

DIR="$(realpath "$1")"
VERSION="$2"
WORKSPACE="$(bazel info workspace)"
ARTIFACTS=(
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$VERSION"
    "org.jetbrains.kotlin:kotlin-reflect:$VERSION"
    "org.jetbrains.kotlin:kotlin-script-runtime:$VERSION"
    "org.jetbrains.kotlin:kotlin-android-extensions-runtime:$VERSION"
    "org.jetbrains.kotlin:kotlin-gradle-plugin:$VERSION"
)

mkdir -p "$DIR"
touch "$DIR/BUILD"

bazel run --config=rcache //tools/base/bazel:third_party_build_generator -- \
    "$DIR/BUILD" \
    "$WORKSPACE/prebuilts/tools/common/m2/repository" \
    "${ARTIFACTS[@]}"
