set(PREBUILTS "${CMAKE_CURRENT_LIST_DIR}/../../../../../prebuilts")
set(CMAKE_C_COMPILER   "${PREBUILTS}/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/bin/aarch64-linux-android-gcc" CACHE PATH "C compiler")
set(CMAKE_CXX_COMPILER "${PREBUILTS}/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/bin/aarch64-linux-android-g++" CACHE PATH "CXX compiler")

include(${CMAKE_CURRENT_LIST_DIR}/android-arm64-v8a.cmake)
