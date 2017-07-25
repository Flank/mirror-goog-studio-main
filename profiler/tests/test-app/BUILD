load("//tools/base/bazel:android.bzl", "dex_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "test-app_java",
    srcs = glob([
        "src/**/*.java",
    ]),
    deps = [
        "//tools/base/profiler/tests/android-mock",
    ],
)

dex_library(
    name = "test-app",
    jars = [":test-app_java"],
)
