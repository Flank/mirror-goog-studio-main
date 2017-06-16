load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.sdk-common",
    srcs = ["src/main/java"],
    resources = ["src/main/resources"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/layoutlib-api:studio.layoutlib-api",
        "//tools/base/build-system/builder-model:studio.builder-model",
        "//tools/base/common:studio.common",
        "//tools/base/sdklib:studio.sdklib",
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:gson",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/base/build-system/builder-model:studio.builder-model[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/idea/.idea/libraries:kxml2",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/build-system:studio.builder-test-api[module]",
        "//tools/idea/.idea/libraries:easymock-tools[test]",
        "//tools/idea/.idea/libraries:bouncy-castle",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

kotlin_library(
    name = "tools.sdk-common",
    srcs = ["src/main/java"],
    pom = ":pom",
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
    ],
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
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
    ],
    deps = [
        ":tools.sdk-common",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:org.easymock_easymock",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
