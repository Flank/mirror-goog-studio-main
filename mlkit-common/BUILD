load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.mlkit-common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.mlkit-common.iml"],
    lint_baseline = "lint_baseline.xml",
    test_data = ["//prebuilts/tools/common/mlkit/testData"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/tools/common/m2:flatbuffers-java",
        "//prebuilts/tools/common/m2:tensorflow-lite-metadata",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
    ],
)

coverage_java_library(
    name = "tools.mlkit-common",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "@maven//:com.google.flatbuffers.flatbuffers-java",
        "@maven//:com.google.guava.guava",
        "@maven//:org.tensorflow.tensorflow-lite-metadata",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + ["build.gradle"],
    visibility = ["//tools/base/build-system:__pkg__"],
)
