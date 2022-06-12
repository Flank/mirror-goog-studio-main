load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.adblib.tools",
    srcs = ["src"],
    iml_files = ["android.sdktools.adblib.tools.iml"],
    test_srcs = ["test/src"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
    ],
)

kotlin_library(
    name = "adblib.tools",
    srcs = glob([
        "src/com/android/adblib/tools/*.kt",
        "src/com/android/adblib/tools/debugging/*.kt",
    ]),
    friends = [
        "//tools/base/adblib:adblib",
    ],
    resource_strip_prefix = "tools/base/adblib.tools",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/adblib",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

kotlin_library(
    name = "adblib.tools.cli",
    srcs = glob([
        "src/com/android/adblib/tools/cli/*.kt",
    ]),
    friends = [
        ":adblib.tools",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":adblib.tools",
    ],
)

kotlin_test(
    name = "adblib.tools.tests",
    srcs = glob([
        "test/src/**/*.kt",
        "test/src/**/*.java",
    ]),
    friends = [
        "//tools/base/adblib:adblib",
        ":adblib.tools",
        "//tools/base/adblib:adblib.tests.testlib",
    ],
    jvm_flags = ["-Dtest.suite.jar=adblib.tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    deps = [
        ":adblib.tools",
        "//tools/base/adblib:adblib.tests.testlib",
        # For "JarTestSuite"
        "//tools/base/testutils:tools.testutils",
        # For JUnit4 support
        "@maven//:junit.junit",
        # FakeAdbServer
        "//tools/base/fakeadbserver:tools.fakeadbserver",
    ],
)
