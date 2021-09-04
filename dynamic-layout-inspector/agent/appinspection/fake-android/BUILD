package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# Simulate an app that doesn't include androidx.window:window (which is needed
# for foldable device support).
kotlin_library(
    name = "fake_android_without_androidx.window",
    srcs = glob(
        [
            "src/**/*.kt",
            "src/**/*.java",
        ],
        exclude = ["src/androidx/window/layout/*"],
    ),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:androidx.annotation.annotation",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

kotlin_library(
    name = "fake_android",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:androidx.annotation.annotation",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)
