load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:utils.bzl", "fileset")
load(":sdk.bzl", "combine_licenses", "sdk_java_binary", "sdk_package")
load("//tools/base/repository:xjc.bzl", "xsd_to_java")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.sdklib",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.sdklib.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["src/main/resources"],
    test_data = ["//prebuilts/studio/sdk:platforms/latest"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/device_validator:studio.android.sdktools.dvlib",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/repository:studio.android.sdktools.repository",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/device_validator:studio.android.sdktools.dvlib[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:guava-testlib[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)

fileset(
    name = "tools.resources",
    srcs = glob([
        "src/main/resources/**",
        "src/main/java/com/android/sdklib/devices/*.xml",
    ]),
    mappings = {
        "src/main/resources/": "",
        "src/main/java/": "",
    },
)

maven_java_library(
    name = "tools.sdklib",
    srcs = glob(
        ["src/main/java/**/*.java"],
        exclude = ["src/main/java/**/tool/**/*.java"],
    ),
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
        "//tools/base/third_party:jakarta.xml.bind_jakarta.xml.bind-api",
    ],
)

maven_pom(
    name = "tools_pom",
    artifact = "sdklib",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:build_version",
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
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
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
        ":avdmanager_lib",
        ":sdkmanager_lib",
        ":tools.sdklib",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/repository:tools.testlib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava-testlib",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.apache.httpcomponents_httpcore",
    ],
)

maven_java_library(
    name = "avdmanager_lib",
    srcs = glob(["src/main/java/**/tool/AvdManagerCli.java"]),
    pom = ":tools_pom",
    deps = [
        ":tools.sdklib",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

maven_java_library(
    name = "sdkmanager_lib",
    srcs = glob(["src/main/java/**/tool/sdkmanager/*.java"]),
    pom = ":tools_pom",
    deps = [
        ":tools.sdklib",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/repository:tools.repository",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

sdk_java_binary(
    name = "avdmanager",
    default_jvm_opts = {
        "linux": "-Dcom.android.sdkmanager.toolsdir=$APP_HOME",
        "mac": "-Dcom.android.sdkmanager.toolsdir=$APP_HOME",
        "win": "\"-Dcom.android.sdkmanager.toolsdir=%~dp0\\..\"",
    },
    main_class = "com.android.sdklib.tool.AvdManagerCli",
    runtime_deps = [":avdmanager_lib"],
)

sdk_java_binary(
    name = "sdkmanager",
    default_jvm_opts = {
        "linux": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "mac": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "win": "\"-Dcom.android.sdklib.toolsdir=%~dp0\\..\"",
    },
    main_class = "com.android.sdklib.tool.sdkmanager.SdkManagerCli",
    runtime_deps = [":sdkmanager_lib"],
)

sdk_java_binary(
    name = "retrace",
    main_class = "com.android.tools.r8.retrace.Retrace",
    runtime_deps = ["//prebuilts/r8"],
)

sdk_package(
    name = "commandlinetools",
    binaries = [
        ":avdmanager",
        ":sdkmanager",
        ":retrace",
        "//tools/base/apkparser/cli:apkanalyzer",
        # b/135688047
        # "//tools/base/jobb:jobb",
        "//tools/base/lint/cli:lint",
        "//tools/base/misc/screenshot2:screenshot2",
        "//tools/base/profgen/profgen-cli:profgen",
    ],
    sourceprops = "tools_source.properties",
    visibility = ["//visibility:public"],
)

test_suite(
    name = "commandlinetoolstest",
    tests = ["//tools/base/sdklib/src/integrationTest:integration-test.test"],
)

xsd_to_java(
    name = "sdk-common-2",
    binding_deps = ["//tools/base/repository:repo-common-02.xjb"],
    catalog = "src/main/resources/xsd/catalog.xml",
    custom_binding = "src/main/resources/xsd/sdk-common-custom-02.xjb",
    episode = "sdk-common-02.xjb",
    package = "com.android.sdklib.repository.generated.common.v2",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-common-02.xsd",
    deps = [
        ":tools.sdklib",
        "//tools/base/repository:tools.repository",
    ],
)

xsd_to_java(
    name = "sdk-repository-2",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        "//tools/base/repository:generic-02.xjb",
        ":sdk-common-02.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.repository.v2",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-repository-02.xsd",
    deps = [
        ":tools.sdklib",
        "//tools/base/repository:tools.repository",
    ],
)

xsd_to_java(
    name = "sdk-addon-2",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        ":sdk-common-02.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.addon.v2",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-addon-02.xsd",
    deps = [
        ":tools.sdklib",
        "//tools/base/repository:tools.repository",
    ],
)

xsd_to_java(
    name = "sdk-sysimg-2",
    binding_deps = [
        "//tools/base/repository:repo-common-02.xjb",
        ":sdk-common-02.xjb",
    ],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.generated.sysimg.v2",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sdk-sys-img-02.xsd",
    deps = [
        ":tools.sdklib",
        "//tools/base/repository:tools.repository",
    ],
)

xsd_to_java(
    name = "sdk-sites-list-4",
    binding_deps = ["//tools/base/repository:sources/repo-sites-common-1.xjb"],
    catalog = "src/main/resources/xsd/catalog.xml",
    package = "com.android.sdklib.repository.sources.generated.v4",
    src_location = "tools/base/sdklib/src/main/java",
    xsd = "src/main/resources/xsd/sources/sdk-sites-list-4.xsd",
    deps = [
        ":tools.sdklib",
        "//tools/base/repository:tools.repository",
    ],
)
