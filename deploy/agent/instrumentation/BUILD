load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library")

coverage_java_library(
    name = "instrumentation_java",
    srcs = [
        "//tools/base/deploy/sites:Sites.java",
    ] + glob([
        "src/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

dex_library(
    name = "instrumentation",
    dexer = "D8",
    flags = ["--min-api 26"],
    jars = [":instrumentation_java"],
    visibility = ["//visibility:public"],
)
