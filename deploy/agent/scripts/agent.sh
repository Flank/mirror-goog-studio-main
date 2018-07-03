#!/bin/bash

AGENT="libswap.so"
JAR="instrumentation.jar"

AGENT_PATH="$HOME/studio-master-dev/bazel-bin/tools/base/deploy/agent/native/android/x86/$AGENT"
JAR_PATH="$HOME/studio-master-dev/bazel-out/k8-fastbuild/genfiles/tools/base/deploy/agent/instrumentation/$JAR"

# First argument is the name of the package to attach
# Second argument is the name of the activity to start, if any.
function install_agent() {
  adb push "$AGENT_PATH" "/data/local/tmp"
  adb push "$JAR_PATH" "/data/local/tmp"
  adb exec-out chmod -R 777 "/data/local/tmp/$AGENT" "/data/local/tmp/$JAR"

  echo "OK"

  adb shell run-as $1 mkdir "ir"
  adb shell run-as $1 cp "/data/local/tmp/$AGENT" "/data/data/$1/ir"
  adb shell run-as $1 cp "/data/local/tmp/$JAR" "/data/data/$1/ir"

  # Stop the currently running activity, since trying to attach the
  # same agent with different .so files will crash it anyways.
  adb exec-out cmd activity force-stop "$1"
  adb exec-out cmd activity start-activity -W "$1/.$2"
}

# First argument is the name of the .so file
# Second argument is the name of the package to attach.
function attach_agent() {
  local pid=$(adb exec-out pidof "$1")

  #<package_name>,<dex_path>,<should_restart>,<instrumentation_path>
  local args="$1,fake.dex,true,/data/data/$1/ir/$JAR"
  echo "$args"
  adb exec-out cmd activity attach-agent "$pid" "/data/data/$1/ir/$AGENT=$args"
}

if [ -z "$3" ]; then
  install_agent "$@"
fi

attach_agent "$@"


