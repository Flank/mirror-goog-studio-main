#!/bin/bash

if [ "$1" != "path" ]; then
  echo "Command $1 is unknown"
  exit 1
fi

if [ -z $2 ]; then
  echo "Missing package name argument"
  exit 1
fi

# Since bazel create symbolic links, we MUST use -L if we use -type f
find -L tools/base/deploy/installer/tests/data/app/$2 -type f -exec echo package:{} \;