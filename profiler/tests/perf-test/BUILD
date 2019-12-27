load(":perf_test.bzl", "perf_test")
load("//tools/base/bazel:android.bzl", "dex_library")

package(
    default_testonly = True,
)

dex_library(
    name = "profiler-service",
    jars = ["//tools/base/profiler/app:studio-profiler"],
)

perf_test(
    name = "BasicTest",
    size = "small",
    srcs = ["testSrcs/com/android/tools/profiler/BasicTest.java"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = ["//tools/idea/.idea/libraries:truth"],
)
