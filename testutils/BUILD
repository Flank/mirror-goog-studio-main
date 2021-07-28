load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "maven_library", "kotlin_test")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.testutils",
    data = [
        # For TestUtils.getKotlinVersionForTests.
        "//tools/buildSrc/base:dependencies.properties",
    ],
    iml_files = ["android.sdktools.testutils.iml"],
    test_srcs = ["src/main/java"],
    test_tags = ["manual"],  # src/main/java is TEST scoped but contains no runnable tests
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:dexlib2[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/.idea/libraries:asm-tools[test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.testutils-tests",
    iml_files = ["testutils-tests.iml"],
    test_data = ["//prebuilts/studio/sdk:platforms/latest"] + glob(["src/test/data/**"]),
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:dexlib2[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/.idea/libraries:truth-java8-extension",
    ],
)

maven_library(
    name = "tools.testutils_bzl",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools:testutils:" + BASE_VERSION,
    data = [
        # For TestUtils.getKotlinVersionForTests.
        "//tools/buildSrc/base:dependencies.properties",
    ],
    legacy_name = "tools.testutils",
    lint_baseline = "lint_baseline.xml",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/testutils",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations:annotations_bzl",
        "//tools/base/common:common_bzl",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.easymock.easymock",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.smali.dexlib2",
    ],
)

kotlin_test(
    name = "tools.tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    data = ["//prebuilts/studio/sdk:platforms/latest"] + glob(["src/test/data/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    lint_baseline = "lint_baseline_tests.xml",
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.testutils",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth.extensions_truth-java8-extension",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib-jdk8",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-test",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)
