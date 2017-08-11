load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.sdklib",
    srcs = ["src/main/java"],
    iml_files = ["sdklib.iml"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/common:studio.common",
        "//tools/base/device_validator:studio.dvlib",
        "//tools/base/layoutlib-api:studio.layoutlib-api",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/repository:studio.repository",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/base/device_validator:studio.dvlib[module]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/repository:studio.repository[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)

maven_java_library(
    name = "tools.sdklib",
    # TODO: Why are the xml files not under resources?
    srcs = glob(["src/main/java/**/*.java"]),
    pom = ":pom",
    resources = glob(
        include = ["src/main/java/**"],
        exclude = ["src/main/java/**/*.java"],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.commons_commons-compress",
        "//tools/base/third_party:org.apache.httpcomponents_httpclient",
    ],
)

maven_pom(
    name = "pom",
    artifact = "sdklib",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)

java_test(
    name = "tools.tests",
    # TODO: Why are the xml files not under resources?
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(
        include = [
            "src/test/java/**",
            "src/test/resources/**",
        ],
        exclude = ["src/test/java/**/*.java"],
    ),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.sdklib",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.apache.httpcomponents_httpcore",
    ],
)
