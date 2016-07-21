include(${CMAKE_CURRENT_LIST_DIR}/darwin.cmake)

set(STL "${PREBUILTS}/clang/darwin-x86/sdk/3.5/include/c++/v1")
set(JDK "${PREBUILTS}/studio/jdk/mac/Contents/Home")
set(TARGET "-target x86_64-apple-macosx10.11.0")
set(COMMON_FLAGS "${TARGET} -I${JDK}/include -I${JDK}/include/darwin")

set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} ${TARGET}" CACHE STRING "ASM Flags")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMMON_FLAGS}" CACHE STRING "C flags")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMMON_FLAGS} -stdlib=libc++ -I${STL}" CACHE STRING "CXX flags")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS}" CACHE STRING "Executable Library linker flags")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS}" CACHE STRING "Shared Library linker flags")
set(CLANG_TIDY_PATH "${PREBUILTS}/clang/host/darwin-x86/clang-stable/bin/clang-tidy")
