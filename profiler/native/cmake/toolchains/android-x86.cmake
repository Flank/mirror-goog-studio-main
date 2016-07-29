set(CMAKE_SYSTEM_PROCESSOR "x86")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fuse-ld=gold -Wl,--icf=safe")

set(STL         "${PREBUILTS}/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9")
set(INCLUDE_STL "-I${STL}/include -I${STL}/libs/x86/include")
set(LINK_STL    "-L${STL}/libs/x86")

set(SYSROOT "${PREBUILTS}/ndk/r10/platforms/android-21/arch-x86")
set(TARGET "-target i686-linux-android")

include(${CMAKE_CURRENT_LIST_DIR}/android.cmake)
