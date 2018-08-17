#!/bin/bash

mkdir -p .studio/bin
cp ./tools/base/deploy/installer/versioned_installer .studio/bin/installer

function fail {
   echo $1
   exit 1
}

# $1 must be equal to the file to test
function assert_exists {
    if [ ! -f $1 ]; then
      find .
      fail "File $1 does not exist"
    fi
}




