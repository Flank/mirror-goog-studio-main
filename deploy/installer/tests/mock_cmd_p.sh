#!/bin/bash

if [ $1 != "package" ]; then
  echo "Service $1 is unknown"
  exit 1
fi

if [ $2 != "path" ]; then
  echo "Unknown command: path"
  exit 1
fi

if [ -z $3 ]; then
  echo "Missing package name argument"
  exit 1
fi

# Since bazel create symbolic links, we MUST use -L if we use -type f
find -L tools/base/deploy/installer/tests/data/app/$3 -type f -exec echo package:{} \;