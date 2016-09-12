new_local_repository(
    name = 'gmock_repo',
    build_file = 'tools/base/profiler/native/external/gmock.BUILD',
    path = 'external/gmock'
)

new_local_repository(
    name = 'gtest_repo',
    build_file = 'tools/base/profiler/native/external/gtest.BUILD',
    path = 'external/gtest'
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
