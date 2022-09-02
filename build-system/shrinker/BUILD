load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

kotlin_library(
    name = "shrinker",
    srcs = glob([
        "src/main/java/**/*.java",
        "src/main/java/**/*.kt",
    ]),
    lint_baseline = "lint_baseline.xml",
    module_name = "shrinker",
    notice = "notice/NOTICE",
    resource_strip_prefix = "tools/base/build-system/shrinker",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/build-system/aapt2-proto",
        "//tools/base/build-system/builder",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "@maven//:com.google.guava.guava",
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    friends = [":shrinker"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    # Specify gradle-api jar first, as kotlin-daemon-client contains older net.rubygrapefruit.platform classes
    deps = [
        ":shrinker",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
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
