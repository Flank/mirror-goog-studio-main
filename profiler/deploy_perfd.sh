 #!/bin/bash

if [[ "$PWD" != */tools/base/profiler ]]; then
    echo "You must call this script from .../tools/base/profiler"
    exit
fi

host=$1

if [[ -z "$host" ]]; then
    script_name=`basename $0`;
    echo "Usage: $script_name <host>"
    echo "Run again and specify <host>, one of: armeabi-v7a, arm64-v8a, x86"
    exit
fi

perfd_path="../../../out/studio/native/out/$host/perfd"
if [[ ! -e $perfd_path ]]; then
    echo "Perfd binary not found at: $perfd_path"
    echo "Verify host, perhaps rebuild perfd? (see profiler/native/README.md)"
    exit
fi

perfd_install_path="/data/local/tmp/perfd/"
echo "Installing perfd onto device: $perfd_install_path"
adb push $perfd_path $perfd_install_path && adb shell $perfd_install_path/perfd

