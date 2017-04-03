cmake_minimum_required(VERSION 2.8)

# Configure HOST_BUILD_DIR
if((NOT CMAKE_CROSSCOMPILING) AND (NOT HOST_BUILD_DIR))
  if (CMAKE_RUNTIME_OUTPUT_DIRECTORY)
    set(HOST_BUILD_DIR ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
  else()
    set(HOST_BUILD_DIR ${CMAKE_CURRENT_BINARY_DIR})
  endif()
endif()

if(HOST_BUILD_DIR)
  set(PROTOBUF_PROTOC_EXECUTABLE ${HOST_BUILD_DIR}/grpc/third_party/protobuf/protoc)
  set(GRPC_CPP_PLUGIN_PATH ${HOST_BUILD_DIR}/grpc/grpc_cpp_plugin)
endif()

# Enables systrace output
if (NOT TRACE_OUTPUT)
  set(TRACE_OUTPUT ON)
endif()

# Set ROOT dir for every third party dependency
set(EXTERNAL_ROOT_DIR  ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external)
set(BORINGSSL_ROOT_DIR ${EXTERNAL_ROOT_DIR}/boringssl/src)
set(GMOCK_ROOT_DIR     ${EXTERNAL_ROOT_DIR}/googletest/googlemock)
set(GRPC_ROOT_DIR      ${EXTERNAL_ROOT_DIR}/grpc-grpc)
set(GTEST_ROOT_DIR     ${EXTERNAL_ROOT_DIR}/googletest/googletest)
set(NANOPB_ROOT_DIR    ${EXTERNAL_ROOT_DIR}/nanopb-c)
set(PROTOBUF_ROOT_DIR  ${EXTERNAL_ROOT_DIR}/protobuf)
set(ZLIB_ROOT_DIR      ${EXTERNAL_ROOT_DIR}/zlib/src)

set(VENDOR_ROOT_DIR   ${CMAKE_CURRENT_SOURCE_DIR}/../../../vendor)
set(JVMTI_INCLUDE_DIR ${VENDOR_ROOT_DIR}/google/android-ndk/include)

set(protobuf_BUILD_TESTS OFF CACHE BOOL "Build tests")
set(protobuf_WITH_ZLIB OFF CACHE BOOL "Build with zlib support") # Tell protobuf to not try to find zlib
set(HAVE_ZLIB 1) # But still tell it to use it.

# Include additional cmake modules after setting global variables
include(cmake/install.cmake)
include(cmake/linker-script.cmake)
include(cmake/proto_compile.cmake)
include(cmake/test.cmake)

include(cmake/lint.cmake) # It has to be after proto_compile as it depends on it

# Set compiler flags used both for the profiler code and for its third party dependencies
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fno-exceptions")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-exceptions")

# Include gRPC
set(GRPC_INCLUDE_DIR ${GRPC_ROOT_DIR}/include)
set(PROTOBUF_INCLUDE_DIR ${PROTOBUF_ROOT_DIR}/src)
add_subdirectory(${GRPC_ROOT_DIR} ./grpc EXCLUDE_FROM_ALL)

# Set command line flags for profiler code
include_directories(${CMAKE_CURRENT_SOURCE_DIR}
                    ${CMAKE_CURRENT_BINARY_DIR}
                    ${PROTOBUF_INCLUDE_DIR}
                    ${GRPC_INCLUDE_DIR}
                    ${JVMTI_INCLUDE_DIR})

set(WARNING_FLAGS "-Wall -Werror -Wno-missing-braces")
set(CMAKE_POSITION_INDEPENDENT_CODE TRUE)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${WARNING_FLAGS} -std=c11")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${WARNING_FLAGS} -std=c++11 -DOSATOMIC_USE_INLINED=1")

# Add profiler components
add_subdirectory(dex)
add_subdirectory(jvmti)
add_subdirectory(agent)
add_subdirectory(perfd)
add_subdirectory(proto)
add_subdirectory(utils)

# Add test utils directory
add_subdirectory(test)