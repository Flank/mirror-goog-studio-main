load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.layoutinspector",
    srcs = ["src"],
    iml_files = ["layoutinspector.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/common:studio.common"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
