load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.layoutinspector",
    srcs = ["src"],
    iml_files = ["android.sdktools.layoutinspector.iml"],
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    exports = ["//tools/base/common:studio.android.sdktools.common"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
    ],
)
