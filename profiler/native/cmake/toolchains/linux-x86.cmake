include(${CMAKE_CURRENT_LIST_DIR}/linux.cmake)

set(LINK_LIBC)
set(LINK_LIBC "${LINK_LIBC} -L${PREBUILTS}/gcc/linux-x86/x86/x86_64-linux-android-4.9/lib/gcc/x86_64-linux-android/4.9.x/32")
set(LINK_LIBC "${LINK_LIBC} -L${PREBUILTS}/gcc/linux-x86/x86/x86_64-linux-android-4.9/x86_64-linux-android/lib")

set(CMAKE_FIND_ROOT_PATH "${PREBUILTS}/gcc/linux-x86/x86/x86_64-linux-android-4.9/x86_64-linux-android")

include(${CMAKE_CURRENT_LIST_DIR}/android-x86.cmake)
