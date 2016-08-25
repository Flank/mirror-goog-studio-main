# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.builder-test-api",
    srcs = ["builder-test-api/src/main/java"],
    test_srcs = ["builder-test-api/src/test/java"],
    deps = [
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools:idea/lib/guava-18.0",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
    ],
    exports = [
        "//tools/base/ddmlib:studio.ddmlib",
        "//tools:idea/lib/guava-18.0",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "studio.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
    test_srcs = ["manifest-merger/src/test/java"],
    deps = [
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools:idea/lib/gson-2.5",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-all/1.9.5:jar[test]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
    exports = ["//tools/base/sdklib:studio.sdklib"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
