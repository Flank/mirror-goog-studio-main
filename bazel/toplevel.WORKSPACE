new_local_repository(
    name = 'gmock_repo',
    build_file = 'tools/base/profiler/native/external/gmock.BUILD',
    path = 'external/googletest/googlemock'
)

new_local_repository(
    name = 'gtest_repo',
    build_file = 'tools/base/profiler/native/external/gtest.BUILD',
    path = 'external/googletest/googletest'
)

new_local_repository(
    name = 'AntennaPod',
    build_file = 'tools/base/build-system/integration-test/AntennaPod.BUILD',
    path = 'external/AntennaPod'
)

new_local_repository(
    name = 'gradle_perf_android_medium',
    build_file = 'tools/base/build-system/integration-test/gradle_perf_android_medium.BUILD',
    path = 'external/gradle-perf-android-medium'
)

new_local_repository(
    name = 'android_studio_gradle_test',
    build_file = 'tools/base/build-system/integration-test/android_studio_gradle_test.BUILD',
    path = 'external/android-studio-gradle-test'
)

local_repository(
    name = "protobuf_repo",
    path = "external/protobuf",
)

local_repository(
    name = "nanopb_repo",
    path = "external/nanopb-c",
)

local_repository(
    name = "zlib_repo",
    path = "external/zlib",
)

local_repository(
    name = "grpc_repo",
    path = "external/grpc-grpc",
)

local_repository(
      name = "intellij_with_bazel",
      path = "tools/vendor/google3/blaze",
)

bind(
    name = "gtest_main",
    actual = "@gtest_repo//:gtest_main"
)

bind(
    name = "gtest",
    actual = "@gtest_repo//:gtest"
)

bind(
    name = "gmock_main",
    actual = "@gmock_repo//:gmock_main"
)

bind(
    name = "protobuf_clib",
    actual = "@protobuf_repo//:protobuf",
)

bind(
    name = "nanopb",
    actual = "@nanopb_repo//:nanopb",
)

bind(
    name = "zlib",
    actual = "@zlib_repo//:zlib",
)

bind(
    name = "protobuf_compiler",
    actual = "@protobuf_repo//:protoc_lib",
)

bind(
    name = "protoc",
    actual = "@protobuf_repo//:protoc",
)

bind(
    name = "grpc_cpp_plugin",
    actual = "@grpc_repo//:grpc_cpp_plugin",
)

bind(
    name = "grpc++_unsecure",
    actual = "@grpc_repo//:grpc++_unsecure",
)

android_ndk_repository(
    name = "androidndk",
    api_level = 21,
)
