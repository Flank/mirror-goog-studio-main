load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "fileset")

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

kotlin_library(
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
    ],
    lint_baseline = "lint_baseline.xml",
    lint_timeout = "long",
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/gradle-core",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        ":jacoco.core_neverlink",
        ":jacoco.report_neverlink",
        ":jetbrains.kotlin-gradle-plugin_neverlink",
        "//tools/analytics-library/crash:tools.analytics-crash",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/aaptcompiler",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-model",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        # Note: This auto-value dependency is not actually used. Bundletool depends on an
        # older version of auto-value which is then not copied into a maven_repo for building
        # data binding's runtime libraries. This forces the newer version until bundletool
        # can be updated once https://github.com/google/bundletool/pull/76 is released.
        "//tools/base/third_party:com.google.auto.value_auto-value",
        "//tools/base/third_party:com.google.auto.value_auto-value-annotations",
        "//tools/base/third_party:com.google.crypto.tink_tink",
        "//tools/base/third_party:com.google.testing.platform_core-proto",
        "//tools/base/third_party:com.android.tools.build.jetifier_jetifier-core",
        "//tools/base/third_party:com.android.tools.build.jetifier_jetifier-processor",
        "//tools/base/third_party:com.android.tools.build_aapt2-proto",
        "//tools/base/third_party:com.android.tools.build_bundletool",
        "//tools/base/third_party:com.android.tools.build_transform-api",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.google.protobuf_protobuf-java-util",
        "//tools/base/third_party:com.squareup_javapoet",
        "//tools/base/third_party:com.squareup_javawriter",
        "//tools/base/third_party:com.sun.istack_istack-commons-runtime",
        "//tools/base/third_party:commons-io_commons-io",  # TODO: remove?
        "//tools/base/third_party:net.sf.jopt-simple_jopt-simple",
        "//tools/base/third_party:org.apache.commons_commons-compress",  # TODO: remove?
        "//tools/base/third_party:org.apache.httpcomponents_httpmime",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",  # required by //tools/base/build-system:gradle-api
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains_annotations",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-analysis",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
        "//tools/base/third_party:org.ow2.asm_asm-util",
        "//tools/base/utp/android-device-provider-gradle-proto",
        "//tools/base/utp/android-test-plugin-host-coverage-proto",
        "//tools/base/utp/android-test-plugin-host-retention-proto",
        "//tools/base/utp/android-test-plugin-result-listener-gradle-proto",
        "//tools/base/zipflinger",
        "//tools/data-binding:tools.compilerCommon",
        "//tools/base/third_party:org.tensorflow_tensorflow-lite-metadata",  # For mlkit-common
        "//tools/base/third_party:com.google.flatbuffers_flatbuffers-java",  # For mlkit-common
        "//tools/base/third_party:jakarta.xml.bind_jakarta.xml.bind-api",
    ],
)

maven_java_library(
    name = "jacoco.core_neverlink",
    neverlink = 1,
    exports = ["//tools/base/third_party:org.jacoco_org.jacoco.core"],
)

maven_java_library(
    name = "jacoco.report_neverlink",
    neverlink = 1,
    exports = ["//tools/base/third_party:org.jacoco_org.jacoco.report"],
)

maven_java_library(
    name = "jetbrains.kotlin-gradle-plugin_neverlink",
    # TODO fix kotlin_library rule so neverlink = 1 can be added here
    exports = ["//tools/base/third_party:org.jetbrains.kotlin_kotlin-gradle-plugin"],
)

maven_pom(
    name = "pom",
    artifact = "gradle",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
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
        "//prebuilts/studio/sdk:platforms/latest",
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
        "//prebuilts/tools/common/m2/repository/com/android/tools/desugar_jdk_libs/1.1.5:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/desugar_jdk_libs_configuration/1.1.5:jar",
        "//prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.core/0.8.3:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/7.0:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/7.0:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-tree/7.0:jar",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
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
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:commons-io_commons-io",  # TODO: remove?
        "//tools/base/third_party:io.grpc_grpc-testing",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",  # required by //tools/base/build-system:gradle-api
        "//tools/base/third_party:org.jacoco_org.jacoco.core",
        "//tools/base/third_party:org.jacoco_org.jacoco.report",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-test",
        "//tools/base/third_party:org.jsoup_jsoup",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
        "//tools/base/third_party:org.ow2.asm_asm-util",
        "//tools/base/zipflinger",
        "//tools/data-binding:tools.compilerCommon",
    ],
)
