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

# $1 must be equal to the string to search in the file
# $2 must be equal to the file to search
function assert_exists_in_file {
    if ! grep -q $1 $2 ; then
      find .
      fail "File $2 does not contain $1"
    fi
}

# Parse file $1(remove the 4 bytes at the beginning containing the size of the payload)
# and write the protoc parsed result in $2
function parse_proto_response_and_writeto {
tail -c +5 $1  | prebuilts/tools/common/m2/repository/com/google/protobuf/protoc/3.0.0/* --decode=proto.InstallerResponse tools/base/deploy/proto/deploy.proto > $2
}
