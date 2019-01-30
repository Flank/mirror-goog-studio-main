load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.instant-run-common",
    srcs = ["instant-run-common/src/main/java"],
    iml_files = ["instant-run-common/android.sdktools.instant-run-common.iml"],
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.instant-run-client",
    srcs = ["instant-run-client/src/main/java"],
    iml_files = ["instant-run-client/android.sdktools.instant-run-client.iml"],
    test_resources = ["instant-run-client/src/test/resources"],
    test_srcs = ["instant-run-client/src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/instant-run:studio.android.sdktools.instant-run-common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

java_library(
    name = "instant-run-common",
    srcs = glob(["instant-run-common/src/main/java/**/*.java"]),
    javacopts = [
        "-source 6",
        "-target 6",
    ],
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
    ],
)
