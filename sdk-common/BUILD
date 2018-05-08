load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.sdk-common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.sdk-common.iml"],
    resources = ["src/main/resources"],
    tags = [
        "no_test_windows",
    ],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:gson",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/build-system/builder-test-api:studio.android.sdktools.builder-test-api[module]",
        "//tools/idea/.idea/libraries:easymock-tools[test]",
        "//prebuilts/tools/common/m2/repository/org/bouncycastle/bcpkix-jdk15on/1.56:jar",
        "//prebuilts/tools/common/m2/repository/org/bouncycastle/bcprov-jdk15on/1.56:jar",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
        "//tools/idea/.idea/libraries:jimfs[test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:equalsverifier[test]",
        "//tools/idea/.idea/libraries:aia-manifest-proto-1.0-jarjar",
        "//tools/idea/.idea/libraries:javax-inject",
    ],
)

fileset(
    name = "tools.resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

kotlin_library(
    name = "tools.sdk-common",
    srcs = ["src/main/java"],
    bundled_deps = [
        "//tools/base/sdk-common/aia-manifest-jar-generator:proto",
    ],
    pom = ":pom",
    resource_strip_prefix = "tools/base/sdk-common",
    resources = [":tools.resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:javax.inject_javax.inject",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
    ],
)

java_binary(
    name = "update_google_maven_repository_cache",
    main_class = "com.android.ide.common.repository.UpdateGoogleMavenRepositoryCache",
    runtime_deps = [":tools.sdk-common"],
)

maven_pom(
    name = "pom",
    artifact = "sdk-common",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)

kotlin_test(
    name = "tools.tests",
    srcs = ["src/test/java"],
    data = ["//prebuilts/studio/sdk:platform-tools"],
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    tags = ["no_test_windows"],  # b/77288863
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
    ],
    deps = [
        ":tools.sdk-common",
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
        "//tools/base/third_party:org.easymock_easymock",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
