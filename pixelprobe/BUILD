load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

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
    lint_baseline = "lint_baseline.xml",
    resources = ["src/main/resources"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/chunkio:studio.android.sdktools.chunkio[module]",
    ],
)

maven_library(
    name = "tools.pixelprobe",
    srcs = glob([
        "src/main/java/**/*.java",
        "src/main/generated/**/*.java",
    ]),
    coordinates = "com.android.tools.pixelprobe:pixelprobe:" + BASE_VERSION,
    description = "library to read pixel data and metadata from various image formats",
    notice = "NOTICE",
    pom_name = "Pixel Probe",
    resource_strip_prefix = "tools/base/pixelprobe/src/main/resources",
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/chunkio:tools.chunkio",
    ],
)

coverage_java_test(
    name = "tools.pixelprobe.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.pixelprobe.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//tools/base/testutils:tools.testutils",
    ],
    deps = [
        ":tools.pixelprobe",
        "@maven//:junit.junit",
    ],
)
