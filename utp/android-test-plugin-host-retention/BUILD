load("//tools/base/bazel:kotlin.bzl", "maven_library", "kotlin_test")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "android-test-plugin-host-retention_bzl",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.utp:android-test-plugin-host-retention:" + BASE_VERSION,
    legacy_name = "android-test-plugin-host-retention",
    resource_strip_prefix = "tools/base/utp/android-test-plugin-host-retention/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:common_bzl",
        "//tools/base/emulator/proto:proto_bzl",
        "@maven//:com.google.guava.guava",
        "@maven//:commons-io.commons-io",
        "@maven//:io.grpc.grpc-all",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
        "//tools/base/utp/android-test-plugin-host-retention-proto:android-test-plugin-host-retention-proto_bzl",
        # Don't change dependency order or the test fails. Those plugins bundle kotlinx
        # coroutine jar which may be different version.
        "@maven//:com.google.testing.platform.android-device-provider-local",
        "@maven//:com.google.testing.platform.core-proto",
        "@maven//:com.google.testing.platform.launcher",
    ],
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
        "@maven//:com.google.testing.platform.launcher",
        # Test dependencies.
        ":android-test-plugin-host-retention",
        "@maven//:io.grpc.grpc-stub",
        "@maven//:io.grpc.grpc-testing",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
