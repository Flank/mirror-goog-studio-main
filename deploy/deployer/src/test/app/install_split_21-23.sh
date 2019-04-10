BASE=$1
SPLIT=$2

set -e
set -x

SESSION=$(adb shell pm install-create -t -r)
SESSION="${SESSION/$'\r'/}"
SESSION=${SESSION:34}
SESSION=${SESSION%?}
echo ${SESSION}

adb push $BASE /data/local/tmp/base.apk
adb push $SPLIT /data/local/tmp/split.apk

BASE_SIZE=$(stat -f %z $BASE)
SPLIT_SIZE=$(stat -f %z $SPLIT)
adb shell "cat /data/local/tmp/base.apk | pm install-write -S $BASE_SIZE $SESSION base; echo \$?"
adb shell "cat /data/local/tmp/split.apk | pm install-write -S $SPLIT_SIZE $SESSION split; echo \$?"
adb shell "pm install-commit $SESSION; echo \$?"
