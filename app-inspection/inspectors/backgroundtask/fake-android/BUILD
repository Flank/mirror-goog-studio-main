package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:kotlin.bzl", "kotlin_library_legacy")

kotlin_library_legacy(
    name = "fake_android",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2:androidx.annotation.annotation.1.1.0",
    ],
)
