load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "studio.android.sdktools.pixelprobe",
    # do not sort: must match IML order
    srcs = [
        "src/main/generated",
        "src/main/java",
        "src/test/tools",
    ],
    iml_files = ["android.sdktools.pixelprobe.iml"],
    lint_baseline = "lint_baseline.xml",
    project = "unb",
    resources = ["src/main/resources"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/chunkio:studio.android.sdktools.chunkio[module]",
        "//tools/base/chunkio-processor:studio.android.sdktools.chunkio-processor[module]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
    ],
)
