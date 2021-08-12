load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.dynamic-layout-inspector.common",
    srcs = ["src/main"],
    iml_files = ["dynamic-layout-inspector.common.iml"],
    test_srcs = ["src/test"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:layoutinspector-view-proto",
        "//tools/adt/idea/.idea/libraries:layoutinspector-skia-proto",
    ],
)
