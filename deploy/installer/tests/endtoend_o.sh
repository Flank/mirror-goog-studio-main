#!/bin/bash

# Setup the environment
PACKAGE_NAME=my.fake.app
TEST_BASE_FOLDER=tools/base/deploy/installer/tests
source $TEST_BASE_FOLDER/test_framework_setup.sh

# Invoke dump
.ir2/bin/ir2_installer \
-cmd=$PWD/$TEST_BASE_FOLDER/mock_cmd_o.sh \
-pm=$PWD/$TEST_BASE_FOLDER/mock_pm_o.sh \
dump \
$PACKAGE_NAME

# Check we have the expected files
assert_exists ".ir2/dumps/$PACKAGE_NAME/sample.apk.remoteblock"
assert_exists ".ir2/dumps/$PACKAGE_NAME/sample.apk.remotecd"
