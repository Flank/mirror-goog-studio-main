#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
WORKSPACE=$DIR/../../../..
$WORKSPACE/tools/base/bazel/bazel build //tools/base/deploy/...

adb shell mkdir -p /data/local/tmp/.ir2
adb shell mkdir -p /data/local/tmp/.ir2/bin
adb shell mkdir -p /data/local/tmp/.ir2/dumps
adb shell mkdir -p /data/local/tmp/.ir2/apps

ABI=$(adb shell getprop ro.product.cpu.abi)
adb push $WORKSPACE/bazel-bin/tools/base/deploy/installer/android/$ABI/ir2_installer /data/local/tmp/.ir2/bin
adb push $WORKSPACE/bazel-bin/tools/base/deploy/agent/native/android/$ABI/libswap.so /data/local/tmp/.ir2/bin/agent.so
adb push $WORKSPACE/bazel-genfiles/tools/base/deploy/agent/instrumentation/instrumentation.jar /data/local/tmp/.ir2/bin/support.dex

adb shell chmod +x /data/local/tmp/.ir2/bin/ir2_installer
adb shell "/data/local/tmp/.ir2/bin/ir2_installer swap 1" < "$1"