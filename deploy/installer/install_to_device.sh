#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
WORKSPACE=$DIR/../../../..
$WORKSPACE/tools/base/bazel/bazel build //tools/base/deploy/...

adb shell mkdir -p /data/local/tmp/.studio
adb shell mkdir -p /data/local/tmp/.studio/bin
adb shell mkdir -p /data/local/tmp/.studio/dumps
adb shell mkdir -p /data/local/tmp/.studio/apps

ABI=$(adb shell getprop ro.product.cpu.abi)
adb push $WORKSPACE/bazel-bin/tools/base/deploy/installer/android/$ABI/ir2_installer /data/local/tmp/.studio/bin
adb push $WORKSPACE/bazel-bin/tools/base/deploy/agent/native/android/$ABI/libswap.so /data/local/tmp/.studio/bin/agent.so

adb shell chmod +x /data/local/tmp/.studio/bin/installer
adb shell "/data/local/tmp/.studio/bin/installer swap 1" < "$1"
