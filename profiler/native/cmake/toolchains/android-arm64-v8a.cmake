set(CMAKE_SYSTEM_PROCESSOR "aarch64")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -fuse-ld=gold -Wl,--icf=safe")

set(STL         "${PREBUILTS}/ndk/current/sources/cxx-stl/gnu-libstdc++/4.9")
set(INCLUDE_STL "-I${STL}/include -I${STL}/libs/arm64-v8a/include")
set(LINK_STL    "-L${STL}/libs/arm64-v8a")

set(SYSROOT "${PREBUILTS}/ndk/r10/platforms/android-21/arch-arm64")
set(TARGET "-target aarch64-linux-androideabi")

include(${CMAKE_CURRENT_LIST_DIR}/android.cmake)
