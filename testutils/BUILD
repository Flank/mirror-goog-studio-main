# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.testutils",
    srcs = ["src/main/java"],
    test_srcs = ["src/test/java"],
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools:idea/lib/hamcrest-core-1.3",
        "//tools:idea/lib/junit-4.12",
        "//prebuilts/tools/common/easymock-tools:easymock-3.1",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-all/1.9.5:jar",
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.28:jar",
    ],
    exports = ["//tools/base/common:studio.common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
