#!/bin/bash
set -x
# Setup the environment
PACKAGE_NAME=my.fake.app
TEST_BASE_FOLDER=tools/base/deploy/installer/tests
source $TEST_BASE_FOLDER/test_framework_setup.sh

# Invoke dump
.studio/bin/installer \
-cmd=$PWD/$TEST_BASE_FOLDER/mock_cmd_o.sh \
-pm=$PWD/$TEST_BASE_FOLDER/mock_pm_o.sh \
dump \
$PACKAGE_NAME > raw_output_protobuffer.data

parse_proto_response_and_writeto raw_output_protobuffer.data text_protobuffer.txt
cat text_protobuffer.txt
# Check we have the expected files in the proto response
assert_exists_in_file "sample.apk" "text_protobuffer.txt"
assert_exists_in_file "cd:" "text_protobuffer.txt"
assert_exists_in_file "signature:" "text_protobuffer.txt"
