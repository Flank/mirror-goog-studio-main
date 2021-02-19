load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.usb-devices",
    srcs = ["src"],
    iml_files = ["usb-devices.iml"],
    lint_baseline = "lint_baseline.xml",
    test_data = glob(["testData/**"]),
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)
