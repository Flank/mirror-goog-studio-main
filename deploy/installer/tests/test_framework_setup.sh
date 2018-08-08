#!/bin/bash

mkdir -p .ir2/{bin,dumps}
cp ./tools/base/deploy/installer/ir2_installer .ir2/bin

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




