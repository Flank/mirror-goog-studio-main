if [ $# -eq 0 ]; then
    echo "Usage: bench.sh <name> <times> <package> <apk0> <apk1>"
fi

name=$1
times=$2
package=$3
apk0=$4
apk1=$5
bazel build //tools/base/deploy/deployer:deployer.runner
dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
runner=$dir/../../../bazel-bin/tools/base/deploy/deployer/deployer.runner
adb=$ANDROID_HOME/platform-tools/adb

tmpdb=$(mktemp /tmp/studio.db.XXXXXX)
rm /tmp/studio.db
$runner install $package $apk0
activity=$($adb shell pm dump $package | grep -A 1 "^ *android.intent.action.MAIN:$" | tail -1 | sed "s/ *[^ ]* \([^ ]*\).*/\1/")
$adb shell am start -W $activity

cp /tmp/studio.db $tmpdb
for i in `seq 1 $times`; do
    echo Run $i of $times
    # Do the swap and keep the measurement
    $runner fullswap $package $apk1
    cp /tmp/report.json swap${i}_$name.json
    echo Reverting...
    $runner fullswap $package $apk0
    cp $tmpdb /tmp/studio.db
done
