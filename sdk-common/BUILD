load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test", "maven_library")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.sdk-common",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.sdk-common.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["src/main/resources"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/sdklib:studio.android.sdktools.sdklib",
        "//tools/adt/idea/.idea/libraries:kxml2",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/adt/idea/.idea/libraries:kxml2",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/build-system/builder-test-api:studio.android.sdktools.builder-test-api[module]",
        "//tools/adt/idea/.idea/libraries:easymock[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:jimfs[test]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/.idea/libraries:equalsverifier[test]",
        "//tools/adt/idea/.idea/libraries:javax-inject",
        "//tools/adt/idea/.idea/libraries:aapt-proto",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:aia-proto",
        "//tools/adt/idea/.idea/libraries:studio-proto",
    ],
)

fileset(
    name = "tools.resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

maven_library(
    name = "tools.sdk-common_bzl",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        "//tools/base/sdk-common/aia-manifest-jar-generator:tools.proto",
    ],
    coordinates = "com.android.tools:sdk-common:" + BASE_VERSION,
    legacy_name = "tools.sdk-common",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/sdk-common",
    resources = [":tools.resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/shared:tools.analytics-shared_bzl",
        "//tools/base/annotations:annotations_bzl",
        "//tools/base/build-system/aaptcompiler:aaptcompiler_bzl",
        "//tools/base/build-system/builder-model:builder-model_bzl",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api_bzl",
        "//tools/base/common:common_bzl",
        "//tools/base/ddmlib:tools.ddmlib_bzl",
        "//tools/base/layoutlib-api:tools.layoutlib-api_bzl",
        "//tools/base/repository:tools.repository_bzl",
        "//tools/base/sdklib:tools.sdklib_bzl",
        "@maven//:com.android.tools.build.aapt2-proto",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:jakarta.xml.bind.jakarta.xml.bind-api",
        "@maven//:javax.inject.javax.inject",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.bouncycastle.bcpkix-jdk15on",
        "@maven//:org.bouncycastle.bcprov-jdk15on",
        "@maven//:org.jetbrains.annotations",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:xerces.xercesImpl",
    ],
)

java_binary(
    name = "update_google_maven_repository_cache",
    main_class = "com.android.ide.common.repository.UpdateGoogleMavenRepositoryCache",
    runtime_deps = [":tools.sdk-common"],
)

kotlin_test(
    name = "tools.tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
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
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/repository:tools.testlib",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.android.tools.build_aapt2-proto",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
        "//tools/base/third_party:org.easymock_easymock",
        "//tools/base/third_party:org.jetbrains.intellij.deps_trove4j",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib-jdk8",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + ["build.gradle"],
    visibility = ["//tools/base/build-system:__pkg__"],
)
