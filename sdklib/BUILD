load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load(":sdk.bzl", "sdk_java_binary", "sdk_package", "combine_licenses")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.sdklib",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.sdklib.iml"],
    test_coverage = True,
    test_data = ["//prebuilts/studio/sdk:platforms/latest"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/device_validator:studio.android.sdktools.dvlib",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/repository:studio.android.sdktools.repository",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/device_validator:studio.android.sdktools.dvlib[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
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

maven_java_library(
    name = "tools.sdklib",
    srcs = glob(["src/main/java/**/*.java"]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/sdklib",
    resources = ["tools.resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        # org.apache.httpcomponents:httpclient:4.2.6 depends on
        # commons-logging:commons-logging:1.1.1, but other targets use a newer version.
        # Include the dependeny explicitly to bump the version in gradle's resolved dependency
        # graph, as the custom maven logic in tools/base resolves everything together.
        "//tools/base/third_party:commons-logging_commons-logging",
        "//tools/base/third_party:org.apache.commons_commons-compress",
        "//tools/base/third_party:org.apache.httpcomponents_httpclient",
        # Include explicitly in order to depend on httpcore 4.4.5 instead of 4.4.4
        "//tools/base/third_party:org.apache.httpcomponents_httpcore",
    ],
)

maven_pom(
    name = "pom",
    artifact = "sdklib",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)

kotlin_test(
    name = "tools.tests",
    # TODO: Why are the xml files not under resources?
    srcs = ["src/test/java"],
    coverage = True,
    data = ["//prebuilts/studio/sdk:platforms/latest"],
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(
        include = [
            "src/test/java/**",
            "src/test/resources/**",
        ],
        exclude = [
            "src/test/java/**/*.java",
            "src/test/java/**/*.kt",
        ],
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

sdk_java_binary(
    name = "avdmanager",
    default_jvm_opts = {
        "linux": "-Dcom.android.sdkmanager.toolsdir=$APP_HOME",
        "mac": "-Dcom.android.sdkmanager.toolsdir=$APP_HOME",
        "win": "-Dcom.android.sdkmanager.toolsdir=%~dp0\..",
    },
    main_class = "com.android.sdklib.tool.AvdManagerCli",
    runtime_deps = [":tools.sdklib"],
)

sdk_java_binary(
    name = "sdkmanager",
    default_jvm_opts = {
        "linux": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "mac": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "win": "-Dcom.android.sdklib.toolsdir=%~dp0\..",
    },
    main_class = "com.android.sdklib.tool.sdkmanager.SdkManagerCli",
    runtime_deps = [":tools.sdklib"],
)

sdk_package(
    name = "commandlinetools",
    binaries = [
        ":avdmanager",
        ":sdkmanager",
        "//tools/base/apkparser/cli:apkanalyzer",
        "//tools/base/jobb:jobb",
        "//tools/base/lint/cli:lint",
        "//tools/base/misc/screenshot2:screenshot2",
    ],
    sourceprops = "tools_source.properties",
    visibility = ["//visibility:public"],
)

test_suite(
    name = "commandlinetoolstest",
    tests = ["//tools/base/sdklib/src/integrationTest:integration-test.test"],
)
