load("//tools/base/bazel:bazel.bzl", "iml_module", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.java-lib-model-builder",
    srcs = ["src/main/java"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system/java-lib-plugin/java-lib-model:studio.java-lib-model[module]",
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/idea/.idea/libraries:equalsverifier[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

maven_java_library(
    name = "java-lib-model-builder",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/java-lib-plugin/java-lib-model-builder",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system/java-lib-plugin/java-lib-model",
    ],
)

maven_pom(
    name = "pom",
    artifact = "java-lib-model-builder",
    group = "com.android.java.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

java_test(
    name = "builder_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = ["-Dtest.suite.jar=builder_tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":java-lib-model-builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
    ],
)
