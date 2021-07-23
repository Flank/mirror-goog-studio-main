load("//tools/base/bazel:kotlin.bzl", "kotlin_test", "maven_library")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

maven_library(
    name = "aaptcompiler_bzl",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.build:aaptcompiler:" + BUILD_VERSION,
    legacy_name = "aaptcompiler",
    lint_baseline = "lint_baseline.xml",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/build-system/aaptcompiler",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/shared:tools.analytics-shared_bzl",
        "//tools/analytics-library/tracker:tools.analytics-tracker_bzl",
        "//tools/base/build-system/aapt2-proto:aapt2-proto_bzl",
        "//tools/base/common:common_bzl",
        "//tools/base/layoutlib-api:tools.layoutlib-api_bzl",
        "@maven//:com.google.code.findbugs.jsr305",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:org.bouncycastle.bcpkix-jdk15on",
        "@maven//:org.bouncycastle.bcprov-jdk15on",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
    ]),
    friends = [":aaptcompiler"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":aaptcompiler",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)

maven_pom(
    name = "pom",
    artifact = "aaptcompiler",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
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
