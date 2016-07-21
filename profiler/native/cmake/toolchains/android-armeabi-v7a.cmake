set(CMAKE_SYSTEM_PROCESSOR "armv7-a")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fuse-ld=gold -Wl,--icf=safe")

set(STL         "${PREBUILTS}/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9")
set(INCLUDE_STL "-I${STL}/include -I${STL}/libs/armeabi-v7a/include")
set(LINK_STL    "-L${STL}/libs/armeabi-v7a")

set(SYSROOT "${PREBUILTS}/ndk/r10/platforms/android-21/arch-arm")
set(TARGET "-target arm-linux-androideabi -march=armv7-a -mthumb")

include(${CMAKE_CURRENT_LIST_DIR}/android.cmake)
