#!/bin/sh
set -eu

if [ $(uname) != "Darwin" ]; then
    echo "macOS only"
    exit 1
fi

function realpath() {
   cd $1 && pwd
}

# Creates the directory if it does not exist and returns its absolute path
function make_target_dir() {
  mkdir -p "$1" && realpath "$1"
}

function usage() {
  declare -r prog="${0##*/}"
  cat <<EOF
Usage:
    $prog [-o <out_dit>] [-d <dist_dir>]
Builds libnst64.dylib - library for Mac Touchbar support.
Library is built in <out_dir>  (or "out" if unset).
If <dist_dir> is set,  `libnst64.dylib` artifact is created there.
EOF
  exit 1
}

while getopts 'd:o:' opt; do
  case $opt in
    o) out_dir_option=$OPTARG;;
    d) dist_dir_option=$OPTARG;;
    *) usage ;;
  esac
done
shift $(($OPTIND-1))
(($#==0)) || usage

declare -r script_dir=$(realpath "$(dirname "$0")")
declare -r top=$(realpath "$(dirname "$0")/../../..")
declare -r cmake_bin="$top/prebuilts/studio/sdk/darwin/cmake/3.18.1/bin/cmake"


# Create directories
declare -r out_dir=$(make_target_dir "${out_dir_option:-"out"}")
if [[ -n "${dist_dir_option:-}" ]]; then
  dist_dir="$(make_target_dir "${dist_dir_option}")"
fi

# Make
(
  cd "$out_dir"
  "$cmake_bin" -DCMAKE_BUILD_TYPE=Release "$script_dir/MacTouchBar"
  "$cmake_bin" --build .
)

# Verify
(
  if [ ! -f "$out_dir/libnst64.dylib" ]; then
    echo "Failed to build libnst64.dylib"
    exit 2
  fi

  declare -r archs=$(lipo -archs ${out_dir}/libnst64.dylib)
  echo "libnst64.dylib archs: $archs"
  if [[ ! "$archs" =~ "x86_64" ]]; then
    echo "libnst64.dylib doesn't have x86_64 architecture "
    exit 3
  fi
  if [[ ! "$archs" =~ "arm64" ]]; then
    echo "libnst64.dylib doesn't have arm64 architecture "
    exit 4
  fi
)

# Copy to Dist
[[ -n "${dist_dir:-}" ]] || exit 0
cp "$out_dir/libnst64.dylib"  "$dist_dir"