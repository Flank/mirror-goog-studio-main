# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "assetstudio",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools/base/common:common[module]",
        "//tools/base/layoutlib-api:layoutlib-api[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//tools/base/sdk-common:sdk-common[module]",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-all/1.9.5:jar[test]",
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.28:jar[test]",
    ],
    exports = [
        "//tools/base/common:common",
        "//tools/base/layoutlib-api:layoutlib-api",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
