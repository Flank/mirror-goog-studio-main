#!/bin/bash

# First argument is the name of the .so files
# Second argument is the name of the package to attach
# Third argument is the name of the activity to start, if any.
function install_agent() {
  adb push $1 /data/local/tmp
  adb exec-out chmod -R 777 "/data/local/tmp/$1"
  adb shell run-as $2 mkdir files
  adb shell run-as $2 cp "/data/local/tmp/$1" "/data/data/$2/files/$1"
  
  # Stop the currently running activity, since trying to attach the 
  # same agent with different .so files will crash it anyways.
  adb exec-out cmd activity force-stop "$2"
  adb exec-out cmd activity start-activity -W "$2/.$3" 
}

# First argument is the name of the .so file
# Second argument is the name of the package to attach.
function attach_agent() {
  local pid=$(adb exec-out pidof "$2")
  echo "$pid"
  adb exec-out cmd activity attach-agent "$pid" "/data/data/$2/files/$1=$2"
}

install_agent "$@"
attach_agent "$@"


