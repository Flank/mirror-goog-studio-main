load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.instant-run-common",
    srcs = ["instant-run-common/src/main/java"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/base/annotations:studio.android-annotations[module]"],
)

iml_module(
    name = "studio.instant-run-client",
    srcs = ["instant-run-client/src/main/java"],
    tags = ["managed"],
    test_resources = ["instant-run-client/src/test/resources"],
    test_srcs = ["instant-run-client/src/test/java"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/instant-run:studio.instant-run-common[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)
