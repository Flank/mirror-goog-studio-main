include(${CMAKE_CURRENT_LIST_DIR}/linux.cmake)

set(LINK_LIBC)
set(LINK_LIBC "${LINK_LIBC} -L${PREBUILTS}/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/lib/gcc/aarch64-linux-android/4.9.x")
set(LINK_LIBC "${LINK_LIBC} -L${PREBUILTS}/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/aarch64-linux-android/lib64")

set(CMAKE_FIND_ROOT_PATH "${PREBUILTS}/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/aarch64-linux-android")

include(${CMAKE_CURRENT_LIST_DIR}/android-arm64-v8a.cmake)
