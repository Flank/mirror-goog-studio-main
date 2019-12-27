load("//tools/base/bazel:android.bzl", "dex_library")
load(":perf_test.bzl", "perf_test")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

package(
    default_testonly = True,
)

dex_library(
    name = "profiler-service",
    jars = ["//tools/base/profiler/app:studio-profiler"],
)
