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
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "studio.usb-devices",
    srcs = ["src"],
    iml_files = ["usb-devices.iml"],
    lint_baseline = "lint_baseline.xml",
    project = "unb",
    test_data = glob(["testData/**"]),
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)
