load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

fileset(
    name = "resources",
    srcs = ["//tools/buildSrc/base:version.properties"] + glob(["src/main/resources/**"]),
    mappings = {
        "//tools/buildSrc/base:": "com/android/build/gradle/internal/",
        "src/main/resources/": "",
    },
)

maven_java_library(
    name = "gradle",
    pom = ":pom",
    srcs = glob(["src/main/java/**/*.java"]),
    resource_strip_prefix = "tools/base/build-system/gradle",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library:tools.analytics-protos",
        "//tools/base/annotations:annotations",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder:builder",
        "//tools/base/build-system/builder-model:builder-model",
        "//tools/base/build-system/gradle-api:gradle-api",
        "//tools/base/build-system/gradle-core:gradle-core",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/data-binding:tools.compilerCommon",
        "//tools/external/gradle:gradle-api.jar_neverlink",
    ],
)

maven_pom(
    name = "pom",
    artifact = "gradle",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version"
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
