# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "sdklib",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    test_resources = ["src/test/resources"],
    deps = [
        "//tools/base/common:common[module]",
        "//tools/base/device_validator:dvlib[module]",
        "//tools/base/layoutlib-api:layoutlib-api[module]",
        "//tools:idea/lib/httpcore-4.4.1",
        "//tools:idea/lib/httpmime-4.4.1",
        "//tools:idea/lib/httpclient-4.4.1",
        "//tools:idea/lib/fluent-hc-4.4.1",
        "//tools/adt/idea:android/lib/commons-compress-1.8.1",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.28:jar[test]",
        "//tools:idea/lib/gson-2.5",
        "//tools/base/repository:repository[module]",
        "//tools/base/testutils:testutils[module, test]",
    ],
    exports = [
        "//tools/base/common:common",
        "//tools/base/device_validator:dvlib",
        "//tools/base/layoutlib-api:layoutlib-api",
        "//tools:idea/lib/httpcore-4.4.1",
        "//tools:idea/lib/httpmime-4.4.1",
        "//tools:idea/lib/httpclient-4.4.1",
        "//tools:idea/lib/fluent-hc-4.4.1",
        "//tools/adt/idea:android/lib/commons-compress-1.8.1",
        "//tools:idea/lib/gson-2.5",
        "//tools/base/repository:repository",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
