load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.usb-devices",
    srcs = ["src"],
    iml_files = ["usb-devices.iml"],
    tags = ["managed"],
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)
