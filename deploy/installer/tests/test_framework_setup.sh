#!/bin/bash

mkdir -p .studio/{bin,dumps}
cp ./tools/base/deploy/installer/installer .studio/bin

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




