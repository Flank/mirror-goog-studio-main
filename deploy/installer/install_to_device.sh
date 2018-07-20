#!/bin/bash

echo "Be sure to modify MY_HOME to point to where your studio-master-dev branch lives!"
echo "If this fails, it's probably because of that."
MY_HOME=$HOME

$MY_HOME/studio-master-dev/tools/base/bazel/bazel build //tools/base/deploy/...

adb shell mkdir -p /data/local/tmp/.ir2
adb shell mkdir -p /data/local/tmp/.ir2/bin
adb shell mkdir -p /data/local/tmp/.ir2/dumps
adb shell mkdir -p /data/local/tmp/.ir2/apps

adb push $MY_HOME/studio-master-dev/bazel-bin/tools/base/deploy/installer/android/x86/ir2_installer /data/local/tmp/.ir2/bin
adb push $MY_HOME/studio-master-dev/bazel-bin/tools/base/deploy/agent/native/android/x86/libswap.so /data/local/tmp/.ir2/bin/agent.so
adb push $MY_HOME/studio-master-dev/bazel-out/k8-fastbuild/genfiles/tools/base/deploy/agent/instrumentation/instrumentation.jar /data/local/tmp/.ir2/bin/support.dex

adb shell chmod +x /data/local/tmp/.ir2/bin/ir2_installer
adb shell "/data/local/tmp/.ir2/bin/ir2_installer swap 1" < "$1"