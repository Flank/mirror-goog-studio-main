include(${CMAKE_CURRENT_LIST_DIR}/linux.cmake)

set(LINK_LIBC)
set(LINK_LIBC "${LINK_LIBC} -L${PREBUILTS}/gcc/linux-x86/arm/arm-linux-androideabi-4.9/lib/gcc/arm-linux-androideabi/4.9.x/armv7-a/thumb")
set(LINK_LIBC "${LINK_LIBC} -L${PREBUILTS}/gcc/linux-x86/arm/arm-linux-androideabi-4.9/arm-linux-androideabi/lib/armv7-a/thumb")

set(CMAKE_FIND_ROOT_PATH "${PREBUILTS}/gcc/linux-x86/arm/arm-linux-androideabi-4.9/arm-linux-androideabi")

include(${CMAKE_CURRENT_LIST_DIR}/android-armeabi-v7a.cmake)
