package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# Simulate an app that doesn't include androidx.window:window (which is needed
# for foldable device support).
kotlin_library(
    name = "fake_android",
    srcs = glob(
        [
            "src/**/*.kt",
            "src/**/*.java",
        ],
        exclude = ["src/libraries/**"],
    ),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "@maven//:androidx.annotation.annotation",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

kotlin_library(
    name = "androidx.window-beta02",
    srcs = glob([
        "src/libraries/androidx-window-beta-02/**/*.kt",
        "src/libraries/androidx-window-beta-02/**/*.java",
    ]),
    deps = [":fake_android"],
)

kotlin_library(
    name = "androidx.window-beta03",
    srcs = glob([
        "src/libraries/androidx-window-beta-03/**/*.kt",
        "src/libraries/androidx-window-beta-03/**/*.java",
    ]),
    deps = [":fake_android"],
)

kotlin_library(
    name = "androidx.window-beta04",
    srcs = glob([
        "src/libraries/androidx-window-beta-04/**/*.kt",
        "src/libraries/androidx-window-beta-04/**/*.java",
    ]),
    deps = [":fake_android"],
)
