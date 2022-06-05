load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:utils.bzl", "dir_archive", "fileset")
load("//tools/base/common:version.bzl", "BASE_VERSION", "BUILD_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.common.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    # do not sort: must match IML order
    resources = [
        "src/main/resources",
        "version",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/base/annotations:studio.android.sdktools.android-annotations"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
    ],
)

java_test(
    name = "latest_version",
    data = ["//tools/buildSrc/base:version.properties"],
    jvm_flags = [
        "-Dtest.version.properties=$(location //tools/buildSrc/base:version.properties)",
        "-Dtest.version.baseVersion=" + BASE_VERSION,
        "-Dtest.version.buildVersion=" + BUILD_VERSION,
    ],
    test_class = "com.android.VersionTest",
    runtime_deps = ["studio.android.sdktools.common.tests_testlib"],
)

fileset(
    name = "tools.common.resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

fileset(
    name = "version.properties",
    srcs = ["//tools/buildSrc/base:version.properties"],
    mappings = {"//tools/buildSrc/base:": "com/android/"},
)

maven_library(
    name = "tools.common",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools:common:" + BASE_VERSION,
    description = "common library used by other Android tools libraries.",
    module_name = "common",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/common",
    resources = [
        ":tools.common.resources",
        ":version.properties",
    ],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/base/annotations",
        "@maven//:com.google.guava.guava",
    ],
    deps = [
        "@maven//:net.java.dev.jna.jna-platform",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

kotlin_test(
    name = "tools.tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.common",
        ":tools.fixtures",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.common.tests",
    iml_files = ["android.sdktools.common.tests.iml"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/base/annotations:studio.android.sdktools.android-annotations"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
    ],
)

kotlin_library(
    name = "tools.fixtures",
    srcs = glob([
        "src/testFixtures/java/**/*.kt",
        "src/testFixtures/java/**/*.java",
    ]),
    resources = glob(["src/testFixtures/resources/**"]),
    visibility = ["//visibility:public"],  # contains com.android.repository.testframework
    deps = [
        ":tools.common",
        "//tools/base/annotations",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.common.testfixtures",
    srcs = ["src/testFixtures/java"],
    iml_files = ["android.sdktools.common.testfixtures.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/base/annotations:studio.android.sdktools.android-annotations"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
    ],
)
