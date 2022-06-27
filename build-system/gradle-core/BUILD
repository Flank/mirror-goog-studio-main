load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

fileset(
    name = "resources",
    srcs = glob([
        "src/main/resources/**",
        "src/fromGradle/resources/**",
    ]) + [
        "//prebuilts/tools/common/aapt:aapt2_version.properties",
    ],
    mappings = {
        "src/main/resources/": "",
        "src/fromGradle/resources/": "",
        "//prebuilts/tools/common/aapt:aapt2_version.properties": "com/android/build/gradle/internal/res/aapt2_version.properties",
    },
)

java_proto_library(
    name = "proto",
    srcs = glob(["src/main/proto/*.proto"]),
    resource_strip_prefix = "tools/base/build-system/gradle-core/",
    visibility = ["//visibility:public"],
)

maven_library(
    name = "gradle-core",
    srcs = glob([
        "src/fromGradle/java/**/*.kt",
        "src/fromGradle/java/**/*.java",
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        ":proto",
        "//tools/base/mlkit-common:tools.mlkit-common",
        "//tools/base/profgen/profgen",
        ":jacoco.core_neverlink",
        ":jacoco.report_neverlink",
        ":jetbrains.kotlin-gradle-plugin_neverlink",
        "//tools/base/build-system:gradle-api_neverlink",
    ],
    coordinates = "com.android.tools.build:gradle:" + BUILD_VERSION,
    lint_baseline = "lint_baseline.xml",
    lint_timeout = "long",
    manifest_lines = ["Plugin-Version: unspecified"],
    module_name = "gradle_core",
    resource_strip_prefix = "tools/base/build-system/gradle-core",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    exports = [
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-api",
    ],
    deps = [
        "//tools/analytics-library/crash:tools.analytics-crash",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/base/build-system/aapt2-proto",
        "//tools/base/build-system/aaptcompiler",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-settings-api",
        "//tools/base/common:tools.common",  # TODO: Not in the pom, but required to compile (?)
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-model",
        "//tools/base/lint/libs/lint-typedef-remover",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/utp/android-device-provider-ddmlib-proto",
        "//tools/base/utp/android-device-provider-gradle-proto",
        "//tools/base/utp/android-test-plugin-host-additional-test-output-proto",
        "//tools/base/utp/android-test-plugin-host-coverage-proto",
        "//tools/base/utp/android-test-plugin-host-retention-proto",
        "//tools/base/utp/android-test-plugin-result-listener-gradle-proto",
        "//tools/data-binding:tools.baseLibrary",
        "//tools/data-binding:tools.baseLibrarySupport",
        "//tools/data-binding:tools.compilerCommon",
        "@maven//:com.android.tools.build.bundletool",
        "@maven//:com.android.tools.build.jetifier.jetifier-core",
        "@maven//:com.android.tools.build.jetifier.jetifier-processor",
        "@maven//:com.android.tools.build.transform-api",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.crypto.tink.tink",
        "@maven//:com.google.flatbuffers.flatbuffers-java",  # For mlkit-common
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.protobuf.protobuf-java-util",
        "@maven//:com.google.testing.platform.core-proto",
        "@maven//:com.squareup.javapoet",
        "@maven//:commons-io.commons-io",  # TODO: remove?
        "@maven//:io.grpc.grpc-core",
        "@maven//:io.grpc.grpc-netty",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-stub",
        "@maven//:net.sf.jopt-simple.jopt-simple",
        "@maven//:org.apache.httpcomponents.httpmime",
        "@maven//:org.bouncycastle.bcpkix-jdk15on",
        "@maven//:org.codehaus.groovy.groovy-all",  # TODO: Not in the pom, but required to compile (?)
        "@maven//:org.glassfish.jaxb.jaxb-runtime",
        "@maven//:org.jetbrains.dokka.dokka-core",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-analysis",
        "@maven//:org.ow2.asm.asm-commons",
        "@maven//:org.ow2.asm.asm-util",
        "@maven//:org.tensorflow.tensorflow-lite-metadata",  # For mlkit-common
    ],
)

java_library(
    name = "jacoco.core_neverlink",
    neverlink = 1,
    exports = ["@maven//:org.jacoco.org.jacoco.core"],
)

java_library(
    name = "jacoco.report_neverlink",
    neverlink = 1,
    exports = ["@maven//:org.jacoco.org.jacoco.report"],
)

java_library(
    name = "jetbrains.kotlin-gradle-plugin_neverlink",
    #neverlink = 1,
    exports = ["@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin"],
)

fileset(
    name = "TestData",
    srcs = glob(["src/test/data/**"]),
)

kotlin_test(
    name = "tests",
    size = "large",
    timeout = "long",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    data = [
        ":TestData",
        "//prebuilts/studio/jdk:jdk11",
        "//prebuilts/studio/sdk:add-ons/addon-google_apis-google-latest",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-24",
        "//prebuilts/studio/sdk:platforms/android-27",
        "//tools/base/build-system:android_platform_for_tests",
        "//tools/base/build-system/aapt2:aapt2_for_tests",
    ],
    friends = [":gradle-core"],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        # TODO: Make it easier to configure AndroidLocation per project.
        "-DANDROID_PREFS_ROOT=/tmp/android_prefs_root",
    ],
    resources = glob(["src/test/resources/**"]),
    tags = [
        "slow",
    ],
    # TODO: Remove the exclude list, once NDK is checked in.
    test_class = "com.android.build.gradle.internal.GradleCoreBazelSuite",
    # Specify gradle-api jar first, as kotlin-daemon-client contains older net.rubygrapefruit.platform classes
    deps = ["//tools/base/build-system:gradle-api"] + [
        ":gradle-core",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder:fixtures",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-model",
        "//tools/base/repository:tools.repository",
        "//tools/base/repository:tools.testlib",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/zipflinger",
        "//tools/data-binding:tools.compilerCommon",
        "@maven//:com.android.tools.desugar_jdk_libs",
        "@maven//:com.android.tools.desugar_jdk_libs_configuration",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.truth.extensions.truth-java8-extension",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.truth.truth",
        "@maven//:commons-io.commons-io",  # TODO: remove?
        "@maven//:io.grpc.grpc-testing",
        "@maven//:junit.junit",
        "@maven//:nl.jqno.equalsverifier.equalsverifier",
        "@maven//:org.codehaus.groovy.groovy-all",  # required by //tools/base/build-system:gradle-api
        "@maven//:org.jacoco.org.jacoco.core",
        "@maven//:org.jacoco.org.jacoco.report",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
        "@maven//:org.jsoup.jsoup",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
        "@maven//:org.ow2.asm.asm-tree",
        "@maven//:org.ow2.asm.asm-util",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/fromGradle/**/*.java",
        "src/fromGradle/resources/**",
        "src/main/**/*.kt",
        "src/main/proto/**/*.proto",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
        "NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
