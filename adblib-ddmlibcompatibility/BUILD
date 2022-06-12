load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.adblib.ddmlibcompatibility",
    srcs = ["src"],
    iml_files = ["android.sdktools.adblib.ddmlibcompatibility.iml"],
    test_srcs = ["test/src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
    ],
)

kotlin_library(
    name = "adblib.ddmlibcompatibility",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    friends = [
        "//tools/base/adblib:adblib",
        "//tools/base/adblib-tools:adblib.tools",
    ],
    resource_strip_prefix = "tools/base/adblib.ddmlibcompatibility",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/adblib",
        "//tools/base/adblib-tools:adblib.tools",
        "//tools/base/ddmlib:ddmlib-base",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

kotlin_test(
    name = "adblib.ddmlibcompatibility.tests",
    srcs = glob([
        "test/src/**/*.kt",
        "test/src/**/*.java",
    ]),
    friends = [
        "//tools/base/adblib:adblib",
        ":adblib.ddmlibcompatibility",
        "//tools/base/adblib:adblib.tests.testlib",
    ],
    jvm_flags = ["-Dtest.suite.jar=adblib.ddmlibcompatibility.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    deps = [
        ":adblib.ddmlibcompatibility",
        "//tools/base/adblib:adblib.tests.testlib",
        "//tools/base/adblib-tools:adblib.tools.tests.testlib",
        # For "JarTestSuite"
        "//tools/base/testutils:tools.testutils",
        # For JUnit4 support
        "@maven//:junit.junit",
        # FakeAdbServer
        "//tools/base/fakeadbserver:tools.fakeadbserver",
        # FakeAdbRule
        "//tools/base/ddmlib:test-utils",
    ],
)
