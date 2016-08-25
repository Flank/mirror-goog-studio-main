# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.pixelprobe",
    srcs = [
        "src/main/generated",
        "src/main/java",
        "src/test/tools",
    ],
    test_srcs = ["src/test/java"],
    resources = ["src/main/resources"],
    test_resources = ["src/test/resources"],
    deps = [
        "//tools/base/chunkio:studio.chunkio[module]",
        "//tools/base/chunkio-processor:studio.chunkio-processor[module]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//tools:idea/lib/junit-4.12[test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)
