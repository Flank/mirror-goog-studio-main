include(${CMAKE_CURRENT_LIST_DIR}/common.cmake)

set(CMAKE_C_COMPILER   "${PREBUILTS}/clang/host/darwin-x86/clang-stable/bin/clang" CACHE PATH "C compiler")
set(CMAKE_CXX_COMPILER "${PREBUILTS}/clang/host/darwin-x86/clang-stable/bin/clang++" CACHE PATH "CXX compiler")
