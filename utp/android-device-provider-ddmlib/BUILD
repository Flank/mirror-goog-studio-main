load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

kotlin_library(
    name = "android-device-provider-ddmlib",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    pom = ":pom",
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.kotlinx_kotlinx-coroutines-core",

        # Don't change dependency order or the test fails. Those plugins bundle kotlinx
        # coroutine jar which may be different version.
        "//tools/base/third_party:com.google.testing.platform_android-device-provider-local",
        "//tools/base/third_party:com.google.testing.platform_core-proto",
        "//tools/base/third_party:com.google.testing.platform_launcher",
    ],
)

maven_pom(
    name = "pom",
    artifact = "android-device-provider-ddmlib",
    group = "com.android.tools.utp",
    source = "//tools/buildSrc/base:base_version",
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        # Don't change dependency order or the test fails. This launcher jar
        # should be loaded first.
        "//prebuilts/tools/common/m2/repository/com/google/testing/platform/launcher/0.0.8-alpha04:jar",

        # Test dependencies.
        ":android-device-provider-ddmlib",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
