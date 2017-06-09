load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.analyzer",
    srcs = ["src/main/java"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//prebuilts/tools/common/archive-patcher:explainer",
        "//prebuilts/tools/common/archive-patcher:generator",
        "//prebuilts/tools/common/archive-patcher:shared",
        "//tools/idea/.idea/libraries:dexlib2",
        "//prebuilts/tools/common/m2/repository/org/smali/baksmali/2.2.1:jar",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jimfs[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/apkparser:studio.binary-resources[module]",
    ],
)
