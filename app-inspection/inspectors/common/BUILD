package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

kotlin_library(
    name = "app-inspection.inspectors.common",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "@maven//:androidx.annotation.annotation",
    ],
)
