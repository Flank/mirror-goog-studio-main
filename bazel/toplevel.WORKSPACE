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
