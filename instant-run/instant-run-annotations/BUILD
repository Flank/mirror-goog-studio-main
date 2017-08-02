java_library(
    name = "instant-run-annotations",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    visibility = ["//visibility:public"],
)

load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.instant-run-annotations",
    srcs = ["src/main/java"],
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/base/annotations:studio.android-annotations[module]"],
)
