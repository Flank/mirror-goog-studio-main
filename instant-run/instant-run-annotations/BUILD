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
    name = "studio.android.sdktools.instant-run-annotations",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.instant-run-annotations.iml"],
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
