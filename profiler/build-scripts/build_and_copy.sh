#!/bin/bash
# When we update the perfetto version in the manifest.xml we should rebuild the binaries using this script.
# This helps guarentee that our binaries match the protos, and give us a snapshot of the code we are shipping.


# Navigate to directory to copy binaries to.
pushd ../../../../prebuilts/tools/common/perfetto
PERFETTO_BINARY_DIR=$(pwd)
popd

pushd ../../../../external/perfetto
echo ""
echo "Updating build tools"
echo ""

./tools/install-build-deps

echo ""
echo "Building release configs."
echo ""
./tools/gn gen out/android_release_arm64 --args='target_os="android" is_clang=true is_debug=false target_cpu="arm64" extra_ldflags="-s"'
./tools/gn gen out/android_release_arm --args='target_os="android" is_clang=true is_debug=false target_cpu="arm" extra_ldflags="-s"'
# Due to a bug in the compiler x86 thinks it needs 8 bytes alignment when really 4 will suffice so we supress this warning.
./tools/gn gen out/android_release_x86 --args='target_os="android" is_clang=true is_debug=false target_cpu="x86" extra_cflags="-Wno-over-aligned" extra_ldflags="-s"'
./tools/gn gen out/android_release_x64 --args='target_os="android" is_clang=true is_debug=false target_cpu="x64" extra_ldflags="-s"'

echo ""
echo "Compiling perfetto, traced, traced_probes for all configs"
echo ""
./tools/ninja -C ./out/android_release_arm64 perfetto traced traced_probes
./tools/ninja -C ./out/android_release_arm perfetto traced traced_probes
./tools/ninja -C ./out/android_release_x86 perfetto traced traced_probes
./tools/ninja -C ./out/android_release_x64 perfetto traced traced_probes


echo ""
echo "Moving binaries to prebuilts dir"
echo ""
process_list=("perfetto" "traced" "traced_probes" "libperfetto.so")
for proc in "${process_list[@]}"
do
	cp ./out/android_release_arm64/$proc $PERFETTO_BINARY_DIR/arm64-v8a/
	cp ./out/android_release_arm/$proc $PERFETTO_BINARY_DIR/armeabi-v7a/
	cp ./out/android_release_x86/$proc $PERFETTO_BINARY_DIR/x86/
	cp ./out/android_release_x64/$proc $PERFETTO_BINARY_DIR/x86_64/
done

echo "Done"