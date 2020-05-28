load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.mlkit-common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.mlkit-common.iml"],
    test_data = ["//prebuilts/tools/common/mlkit/testData"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/tools/common/m2/repository/com/google/flatbuffers/flatbuffers-java/1.12.0:jar",
        "//prebuilts/tools/common/m2/repository/org/tensorflow/tensorflow-lite-metadata/0.1.0-rc1:jar",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:commons-io[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:Objenesis[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
    ],
)

coverage_java_library(
    name = "tools.mlkit-common",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/third_party:com.google.flatbuffers_flatbuffers-java",
        "//tools/base/third_party:org.tensorflow_tensorflow-lite-metadata",
    ],
)
