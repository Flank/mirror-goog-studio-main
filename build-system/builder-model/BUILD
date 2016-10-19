load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.builder-model",
    srcs = ["src/main/java"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = [
         "//tools/base/annotations:studio.android-annotations[module]",
    ]
)

java_library(
    name = "builder-model",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
    ],
)
