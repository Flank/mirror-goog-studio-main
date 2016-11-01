load("//tools/base/bazel:bazel.bzl", "fileset")

fileset(
    name = "resources",
    srcs = ["//tools/buildSrc/base:version.properties"] + glob(["src/main/resources/**"]),
    mappings = {
        "//tools/buildSrc/base:": "com/android/build/gradle/internal/",
        "src/main/resources/": "",
    },
)

java_library(
    name = "gradle",
    srcs = glob(["src/main/java/**/*.java"]),
    resource_strip_prefix = "tools/base/build-system/gradle",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library:analytics-protos",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-api",
        "//tools/base/build-system/gradle-core",
        "//tools/base/build-system/profile",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/data-binding:tools.compilerCommon",
        "//tools/external/gradle:gradle-api.jar",
    ],
)

java_test(
    name = "tests",
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":gradle",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)
