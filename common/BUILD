load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_pom")
load("//tools/base/bazel:utils.bzl", "dir_archive", "fileset")

# do not generate: resources removed manually
iml_module(
    name = "studio.android.sdktools.common",
    srcs = ["src/main/java"],
    bundle_data = [
        "src/main/java/com/android/SdkConstants.java",
    ],
    iml_files = ["android.sdktools.common.iml"],
    res_zips = [":extra_res"],
    resources = ["src/main/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
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

dir_archive(
    name = "extra_res",
    dir = "tools/base/common",
    files = [":version.properties"],
)

kotlin_library(
    name = "tools.common",
    srcs = ["src/main/java"],
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
    srcs = ["src/test/java"],
    coverage = True,
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.common",
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
    test_coverage = True,
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:kotlin-test[test]",
    ],
)
