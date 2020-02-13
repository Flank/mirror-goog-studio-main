#!/bin/bash
# This script updates Emulator proto files in the current directory.
#
# Find an Emulator build here:
# https://android-build.googleplex.com/builds/branches/aosp-emu-master-dev/grid?
set -e

if [ $# == 1 ]
then
build=$1
else
	echo  Usage: $0 build
	exit 1
fi

linux_zip="sdk-repo-linux-emulator-$build.zip"

echo Fetching Emulator build $build
/google/data/ro/projects/android/fetch_artifact --bid $build --target sdk_tools_linux "$linux_zip"
rm *.proto .fetch_*
unzip -j "$linux_zip" emulator/lib/*.proto
rm -f "$linux_zip"
git add .

printf "Update emulator proto files from emu-master-dev build $build\n\nTest: existing\nBug: N/A\n" > commitmsg.tmp

set +e

git commit -s -t commitmsg.tmp

rm -f "commitmsg.tmp"
