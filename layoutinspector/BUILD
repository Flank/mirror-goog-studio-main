load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.intellij.android.layoutInspector",
    srcs = ["src"],
    iml_files = ["intellij.android.layoutInspector.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/common:studio.android.sdktools.common"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
