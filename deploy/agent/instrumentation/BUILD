load("//tools/base/bazel:android.bzl", "dex_library")

java_library(
    name = "instrumentation_java",
    srcs = glob([
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
