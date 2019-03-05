load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.pixelprobe",
    # do not sort: must match IML order
    srcs = [
        "src/main/generated",
        "src/main/java",
        "src/test/tools",
    ],
    iml_files = ["android.sdktools.pixelprobe.iml"],
    resources = ["src/main/resources"],
    test_coverage = True,
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/chunkio:studio.android.sdktools.chunkio[module]",
        "//tools/base/chunkio-processor:studio.android.sdktools.chunkio-processor[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)
