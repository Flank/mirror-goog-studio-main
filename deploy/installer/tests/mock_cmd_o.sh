#!/bin/bash

if [ $1 != "package" ]; then
  echo "Service $1 is unknown"
  exit 1
fi

if [ $2 != "path" ]; then
  echo "Unknown command: path"
  exit 1
fi

exit 1
