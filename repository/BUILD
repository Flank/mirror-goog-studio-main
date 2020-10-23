load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:utils.bzl", "fileset")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.repository",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.repository.iml"],
    lint_baseline = "lint_baseline.xml",
    test_resources = ["src/test/resources"],
    test_srcs = [
        "src/test/java",
        "src/testFixtures/java",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:Guava",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)

fileset(
    name = "tools.resources",
    # TODO: Why are the xml files not under resources?
    srcs = glob(
        include = [
            "src/main/java/**",
        ],
        exclude = [
            "**/*.java",
            "**/*.kt",
        ],
    ),
    mappings = {
        "src/main/java/": "",
    },
)

kotlin_library(
    name = "tools.repository",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/repository",
    resources = [":tools.resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:jakarta.xml.bind_jakarta.xml.bind-api",
        "//tools/base/third_party:org.apache.commons_commons-compress",
        "//tools/base/third_party:org.glassfish.jaxb_jaxb-runtime",
    ],
)

maven_pom(
    name = "pom",
    artifact = "repository",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)

java_library(
    name = "tools.testlib",
    srcs = glob([
        "src/test/java/**",
        "src/testFixtures/java/**",
    ]),
    resources = glob(["src/test/resources/**"]),
    visibility = ["//visibility:public"],  # contains com.android.repository.testframework
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.repository",
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:jakarta.xml.bind_jakarta.xml.bind-api",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.apache.commons_commons-compress",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)

coverage_java_test(
    name = "tools.tests",
    jvm_flags = ["-Dtest.suite.jar=tools.testlib.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [":tools.testlib"],
)
