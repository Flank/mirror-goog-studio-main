#!/bin/bash

if [ "$1" != "path" ]; then
  echo "Command $1 is unknown"
  exit 1
fi

if [ -z $2 ]; then
  echo "Missing package name argument"
  exit 1
fi

find tools/base/deploy/installer/tests/data/app/$2 -exec echo package:{} \;