package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

kotlin_library(
    name = "fake_android",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:androidx.annotation.annotation",
    ],
)
