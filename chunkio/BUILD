load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.chunkio",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.chunkio.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

maven_library(
    name = "tools.chunkio",
    srcs = glob(["src/main/java/**/*.java"]),
    coordinates = "com.android.tools.chunkio:chunkio:" + BASE_VERSION,
    description = "annotations for use with chunkio-processor, and runtime supporting APIs",
    notice = "NOTICE",
    pom_name = "ChunkIO",
    resource_strip_prefix = "tools/base/chunkio",
    visibility = ["//visibility:public"],
)
