load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.pixelprobe",
    srcs = [
        "src/main/generated",
        "src/main/java",
        "src/test/tools",
    ],
    javacopts = ["-extra_checks:off"],
    resources = ["src/main/resources"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/chunkio:studio.chunkio[module]",
        "//tools/base/chunkio-processor:studio.chunkio-processor[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)
