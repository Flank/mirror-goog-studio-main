#!/bin/bash
set -eu

# Build Intellij native tools for Mac

function realpath() {
   cd $1 && pwd
}
# Expected arguments:
declare -r out_dir="$1"
declare -r dist_dir="$2"
declare -r build_number="$3"

declare -r script_dir=$(realpath "$(dirname "$0")")
declare exit_code=0

(
  echo "Building MacTouchBar..."
  ${script_dir}/build-mac-touchbar.sh -o ${out_dir}/MacTouchBar -d ${dist_dir} || ((exit_code++))
)

echo "All Done."
exit $exit_code