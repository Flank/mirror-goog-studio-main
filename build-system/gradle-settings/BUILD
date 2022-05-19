load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:utils.bzl", "fileset")

fileset(
    name = "resources",
    srcs = glob([
        "src/main/resources/**",
    ]),
    mappings = {
        "src/main/resources/": "",
    },
)

kotlin_library(
    name = "gradle-settings",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system/gradle-settings-api",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
        "NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
