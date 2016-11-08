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
    # do not sort: must match IML order
    deps = [
        "//tools/base/instant-run:studio.instant-run-common[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)

java_library(
    name = "instant-run-common",
    srcs = glob(["instant-run-common/src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = ["//tools/base/annotations"],
)

java_library(
    name = "instant-run-client",
    srcs = glob(["instant-run-client/src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":instant-run-common",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-all",
    ],
)
