load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

kotlin_library(
    name = "profgen",
    srcs = glob([
        "src/main/kotlin/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/kotlin/**/*.kt",
    ]),
    data = glob(["testData/**"]),
    friends = [":profgen"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":profgen",
        "//prebuilts/tools/common/m2:com.google.truth.truth.0.42",
        "//prebuilts/tools/common/m2:junit.junit.4.12",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-test",
    ],
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
