load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_pom")
load("//tools/base/bazel:utils.bzl", "dir_archive", "fileset")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.common.iml"],
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
    jvm_flags = ["-Dtest.version.properties=$(location //tools/buildSrc/base:version.properties)"],
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

kotlin_library(
    name = "tools.common",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/common",
    resources = [
        ":tools.common.resources",
        ":version.properties",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

maven_pom(
    name = "pom",
    artifact = "common",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
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
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-test",
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
        "//tools/base/third_party:com.google.guava_guava",
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
