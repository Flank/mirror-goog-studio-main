load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.mlkit-common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.mlkit-common.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
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

# managed by go/iml_to_build [unb]
iml_module(
    name = "unb.studio.android.sdktools.mlkit-common",
    srcs = ["src/main/java"],
    bundled_deps = [
        "//prebuilts/tools/common/m2/repository/com/google/flatbuffers/flatbuffers-java/1.12.0:jar",
        "//prebuilts/tools/common/m2/repository/org/tensorflow/tensorflow-lite-metadata/0.1.0-rc1:jar",
    ],
    iml_files = ["android.sdktools.mlkit-common.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    tags = [
        "manual",
        "unb",
    ],
    test_data = ["//prebuilts/tools/common/mlkit/testData"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/tools/common/m2/repository/com/google/flatbuffers/flatbuffers-java/1.12.0:jar",
        "//prebuilts/tools/common/m2/repository/org/tensorflow/tensorflow-lite-metadata/0.1.0-rc1:jar",
        "//tools/base/testutils:unb.studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/base/common:unb.studio.android.sdktools.common[module]",
    ],
)
