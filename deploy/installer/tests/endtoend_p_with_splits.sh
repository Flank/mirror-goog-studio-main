#!/bin/bash

# Setup the environment
PACKAGE_NAME=my.fake.app.with.splits
TEST_BASE_FOLDER=tools/base/deploy/installer/tests
source $TEST_BASE_FOLDER/test_framework_setup.sh

# Invoke dump
.studio/bin/installer \
-cmd=`pwd`/$TEST_BASE_FOLDER/mock_cmd_p.sh \
dump \
$PACKAGE_NAME > raw_output_protobuffer.data

# Extract, parse, and write protobuffer response to disk
parse_proto_response_and_writeto "raw_output_protobuffer.data" "text_protobuffer.txt"
cat text_protobuffer.txt
# Check we have the expected files
assert_exists_in_file "base.apk" "text_protobuffer.txt"
assert_exists_in_file "split1.apk" "text_protobuffer.txt"
assert_exists_in_file "cd:" "text_protobuffer.txt"
assert_exists_in_file "signature:" "text_protobuffer.txt"
