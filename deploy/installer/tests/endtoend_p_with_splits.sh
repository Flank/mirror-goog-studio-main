#!/bin/bash

# Setup the environment
PACKAGE_NAME=my.fake.app.with.splits
TEST_BASE_FOLDER=tools/base/deploy/installer/tests
source $TEST_BASE_FOLDER/test_framework_setup.sh

# Invoke dump
.ir2/bin/ir2_installer \
-cmd=`pwd`/$TEST_BASE_FOLDER/mock_cmd_p.sh \
dump \
$PACKAGE_NAME

# Check we have the exepted files
assert_exists ".ir2/dumps/$PACKAGE_NAME/base.apk.remoteblock"
assert_exists ".ir2/dumps/$PACKAGE_NAME/base.apk.remotecd"
assert_exists ".ir2/dumps/$PACKAGE_NAME/split1.apk.remoteblock"
assert_exists ".ir2/dumps/$PACKAGE_NAME/split1.apk.remotecd"
