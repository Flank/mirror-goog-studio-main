#!/bin/bash

set -e
set -x
if [ -z ${ANDROID_SDK+x} ]; then 
  echo "ANDROID_SDK is not set"
  exit 1
fi

BUILS_TOOLS_VERSION=28.0.3
SDK_VERSION=28
AAPT2="$ANDROID_SDK/build-tools/$BUILS_TOOLS_VERSION/aapt2"
DX="$ANDROID_SDK/build-tools/$BUILS_TOOLS_VERSION/dx"
ZIPALIGN="$ANDROID_SDK
HAS_RESOURCES=$7
PACKAGE_NAME=$8/build-tools/$BUILS_TOOLS_VERSION/zipalign"
APKSIGNER="$ANDROID_SDK/build-tools/$BUILS_TOOLS_VERSION/apksigner"
PLATFORM="$ANDROID_SDK/platforms/android-$SDK_VERSION/android.jar"

function build_apk() {

  APK=$1
  TEXT=$2
  VERSION=$3
  STRING=$4
  MANIFEST=$5
  SOURCE_FILE=$6
  HAS_RESOURCES=$7
  SPLIT=$8
  PACKAGE=com.example.simpleapp

  echo "AAPT2 compile..."
  FLATS=$(mktemp -d ./mkapk.flats.XXXXXX)
  BIN=$(mktemp -d ./mkapk.bin.XXXXXX)
  GEN_SRC=$(mktemp -d ./mkapk.gen_src.XXXXXX)
  OBJ=$(mktemp -d ./mkapk.obj.XXXXXX)

  sed  "s/%VERSION/$VERSION/" $MANIFEST > $GEN_SRC/AndroidManifest.xml
  sed  -e "s/%PACKAGE/$PACKAGE/" -i "" $GEN_SRC/AndroidManifest.xml
  sed  -e "s/%SPLIT/$SPLIT/" -i "" $GEN_SRC/AndroidManifest.xml

  if [ "$HAS_RESOURCES" = "true" ]; then
    $AAPT2 compile --no-crunch -o $FLATS res/layout/activity_main.xml
    mkdir -p $GEN_SRC/res/values
    sed  "s/%STRING/STRING/" res/values/strings.xml > $GEN_SRC/res/values/strings.xml
    $AAPT2 compile --no-crunch -o $FLATS $GEN_SRC/res/values/strings.xml
    echo "AAPT2 linking (and create R.java) ..."
    $AAPT2 link -o $BIN/base.apk --java $GEN_SRC --manifest $GEN_SRC/AndroidManifest.xml -I $PLATFORM $FLATS/*
  else
    echo "AAPT2 linking (and create R.java) ..."
    $AAPT2 link -o $BIN/base.apk --manifest $GEN_SRC/AndroidManifest.xml -I $PLATFORM 
  fi
  

  echo "Compiling source..."
  if [ "$HAS_RESOURCES" = "true" ]; then
      javac -d $OBJ -classpath src -bootclasspath $PLATFORM -source 1.7 -target 1.7 $GEN_SRC/com/example/simpleapp/R.java
  fi
  sed  "s/%TEXT/$TEXT/" src/com/example/simpleapp/$SOURCE_FILE > $GEN_SRC/$SOURCE_FILE
  javac -d $OBJ -classpath src -classpath $OBJ -bootclasspath $PLATFORM -source 1.7 -target 1.7 $GEN_SRC/$SOURCE_FILE

  echo "Translating in class to dex ..."
  $DX --dex --incremental --output=$BIN/classes.dex $OBJ

  echo "Adding dexs to APK..."
  (cd $BIN; zip -r base.apk classes.dex )

  echo "Aligning and signing APK..."
  # Create keytore via command:
  # keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
  $APKSIGNER sign -v --ks debug.keystore -ks-pass pass:android --min-sdk-version $SDK_VERSION --v1-signing-enabled true --v2-signing-enabled true $BIN/base.apk

  mv $BIN/base.apk $APK

  echo "Cleaning..."
  rm -rf $FLATS
  rm -rf $BIN
  rm -rf $GEN_SRC
  rm -rf $OBJ
}

build_apk ../resource/apks/simple.apk "HelloWorld" "1" "Hello Android!" AndroidManifest.xml MainActivity.java true base
build_apk ../resource/apks/simple+code.apk "HelloWorld2" "1" "Hello Android!" AndroidManifest.xml MainActivity.java true base
build_apk ../resource/apks/simple+ver.apk "HelloWorld" "2" "Hello Android!" AndroidManifest.xml MainActivity.java true base
build_apk ../resource/apks/simple+res.apk "HelloWorld" "1" "Hello Android2!" AndroidManifest.xml MainActivity.java true base
build_apk ../resource/apks/simple+code+res.apk "HelloWorld2" "1" "Hello Android2!" AndroidManifest.xml MainActivity.java true base

build_apk ../resource/apks/split.apk "HelloWorld" "1" "Hello Android!" SplitManifest.xml Data.java false split_01
build_apk ../resource/apks/split2.apk "HelloWorld" "1" "Hello Android!" SplitManifest.xml Data.java false split_02
build_apk ../resource/apks/split+ver.apk "HelloWorld" "2" "Hello Android!" SplitManifest.xml Data.java false split_01
build_apk ../resource/apks/split+code.apk "HelloWorld2" "1" "Hello Android!" SplitManifest.xml Data.java false split_01
