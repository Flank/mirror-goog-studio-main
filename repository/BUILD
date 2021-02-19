load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:utils.bzl", "fileset")
load("xjc.bzl", "xsd_to_java")

exports_files(
    ["src/main/resources/xsd/global.xjb"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.repository",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.repository.iml"],
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
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
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
    deps = [
        ":tools.repository",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
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
    cmd = "path=$$PWD\n while [[ $$path != / ]]\n do\n if [[ -a $$path/DO_NOT_BUILD_HERE ]]\n then\nbreak\nfi\npath=$$(realpath $$path/..)\n done\n cp $$path/DO_NOT_BUILD_HERE $(location root)",
    tags = ["manual"],
    visibility = ["//visibility:public"],
)
