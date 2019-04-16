load("//tools/base/bazel:bazel.bzl", "iml_module", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:utils.bzl", "dir_archive")

fileset(
    name = "version.properties",
    srcs = ["//tools/buildSrc/base:version.properties"],
    mappings = {"//tools/buildSrc/base:": "com/android/builder/model/"},
)

dir_archive(
    name = "extra_res",
    dir = "tools/base/build-system/builder-model",
    files = [":version.properties"],
)

# do not generate: resources removed manually
iml_module(
    name = "studio.android.sdktools.builder-model",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.builder-model.iml"],
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    res_zips = [":extra_res"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)

maven_java_library(
    name = "builder-model",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/builder-model",
    resources = [":version.properties"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
    ],
)

maven_pom(
    name = "pom",
    artifact = "builder-model",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

coverage_java_test(
    name = "tests",
    size = "small",
    srcs = glob(["src/test/java/**"]),
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//tools/base/testutils:tools.testutils",
    ],
    deps = [
        ":builder-model",
        "//tools/base/third_party:junit_junit",
    ],
)
