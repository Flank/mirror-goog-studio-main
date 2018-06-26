#!/bin/bash
../../bazel/bazel build //tools/base/deploy/installer:android
adb shell mkdir /data/local/tmp/.ir2
adb shell mkdir /data/local/tmp/.ir2/bin
adb shell mkdir /data/local/tmp/.ir2/dumps
adb shell mkdir /data/local/tmp/.ir2/apps
adb push ../../../../bazel-bin/tools/base/deploy/installer/android/arm64-v8a/ir2_installer /data/local/tmp/.ir2/bin
