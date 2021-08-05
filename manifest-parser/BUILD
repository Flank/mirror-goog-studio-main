load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test", "coverage_java_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.manifest-parser",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.manifest-parser.iml"],
    resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//prebuilts/tools/common/m2:junit-4.12[test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
    ],
)

coverage_java_library(
    name = "tools.manifest-parser",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/common:tools.common",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.manifest-parser",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
    ],
)
