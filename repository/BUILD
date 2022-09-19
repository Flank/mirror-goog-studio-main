load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:utils.bzl", "fileset")
load("xjc.bzl", "xsd_to_java")
load("//tools/base/common:version.bzl", "BASE_VERSION")

exports_files(
    ["src/main/resources/xsd/global.xjb"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.repository",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.repository.iml"],
    jvm_target = "8",
    resources = ["src/main/resources"],
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
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)

fileset(
    name = "tools.resources",
    srcs = glob(
        ["src/main/resources/**"],
        exclude = ["**/BUILD"],
    ),
    mappings = {
        "src/main/resources/": "",
    },
)

maven_library(
    name = "tools.repository",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools:repository:" + BASE_VERSION,
    description = "A library for downloading and managing package repositories",
    module_name = "repository",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/repository",
    resources = [":tools.resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/base/common:tools.common",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.sun.activation.javax.activation",
        "@maven//:org.apache.commons.commons-compress",
        "@maven//:org.glassfish.jaxb.jaxb-runtime",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

java_library(
    name = "tools.testlib",
    srcs = glob(["src/testFixtures/java/**/*.java"]),
    visibility = ["//visibility:public"],  # contains com.android.repository.testframework
    deps = [
        ":tools.repository",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.jimfs.jimfs",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.repository",
        ":tools.testlib",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.google.truth.truth",
        "@maven//:jakarta.xml.bind.jakarta.xml.bind-api",
        "@maven//:junit.junit",
        "@maven//:org.apache.commons.commons-compress",
        "@maven//:org.mockito.mockito-core",
    ],
)

xsd_to_java(
    name = "repo-common-1",
    catalog = "src/main/resources/xsd/catalog.xml",
    custom_binding = "src/main/resources/xsd/common-custom-01.xjb",
    episode = "repo-common-01.xjb",
    package = "com.android.repository.impl.generated.v1",
    src_location = "tools/base/repository/src/main/java",
    xsd = "src/main/resources/xsd/repo-common-01.xsd",
    deps = [":tools.repository"],
)

xsd_to_java(
    name = "repo-common-2",
    catalog = "src/main/resources/xsd/catalog.xml",
    custom_binding = "src/main/resources/xsd/common-custom-02.xjb",
    episode = "repo-common-02.xjb",
    package = "com.android.repository.impl.generated.v2",
    src_location = "tools/base/repository/src/main/java",
    xsd = "src/main/resources/xsd/repo-common-02.xsd",
    deps = [":tools.repository"],
)

xsd_to_java(
    name = "generic-2",
    binding_deps = [":repo-common-02.xjb"],
    catalog = "src/main/resources/xsd/catalog.xml",
    custom_binding = "src/main/resources/xsd/generic-custom-02.xjb",
    episode = "generic-02.xjb",
    package = "com.android.repository.impl.generated.generic.v2",
    src_location = "tools/base/repository/src/main/java",
    xsd = "src/main/resources/xsd/generic-02.xsd",
    deps = [":tools.repository"],
)

xsd_to_java(
    name = "repo-sites-common-1",
    custom_binding = "src/main/resources/xsd/sources/repo-sites-common-custom-1.xjb",
    episode = "sources/repo-sites-common-1.xjb",
    package = "com.android.repository.impl.sources.generated.v1",
    src_location = "tools/base/repository/src/main/java",
    xsd = "src/main/resources/xsd/sources/repo-sites-common-1.xsd",
    deps = [":tools.repository"],
)

java_binary(
    name = "xjc",
    main_class = "com.sun.tools.xjc.Driver",
    tags = ["manual"],
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//tools/base/jaxb-inheritance-plugin",
    ],
)

genrule(
    name = "find_workspace",
    srcs = ["BUILD"],
    outs = ["root"],
    # iml_to_build doesn't support triple-quote strings, so we have to put everything together here
    cmd = "path=$$(pwd -P); while [[ $$path != / ]]; do if [[ -a $$path/DO_NOT_BUILD_HERE ]]; then break;fi;path=$$(dirname $$(cd $$path && pwd -P)); done; cp $$path/DO_NOT_BUILD_HERE $(location root)",
    local = 1,
    tags = ["manual"],
    visibility = ["//visibility:public"],
)
