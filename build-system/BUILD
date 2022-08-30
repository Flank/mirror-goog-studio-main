load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:gradle.bzl", "gradle_build", "gradle_test")
load("//tools/base/common:version.bzl", "BASE_VERSION", "BUILD_VERSION")
load("//tools/base/bazel:maven.bzl", "maven_library", "maven_repository")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
    iml_files = ["manifest-merger/android.sdktools.manifest-merger.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = ["manifest-merger/src/test/java"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/sdklib:studio.android.sdktools.sdklib"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

maven_library(
    name = "tools.manifest-merger",
    srcs = glob([
        "manifest-merger/src/main/java/**/*.kt",
        "manifest-merger/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.build:manifest-merger:" + BASE_VERSION,
    notice = "manifest-merger/NOTICE",
    resource_strip_prefix = "tools/base/build-system/manifest-merger",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:com.google.code.gson.gson",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

kotlin_test(
    name = "tools.manifest-merger_tests",
    # TODO: Why are the xml files not under resources?
    srcs = glob([
        "manifest-merger/src/test/java/**/*.kt",
        "manifest-merger/src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tools.manifest-merger_tests.jar"],
    resources = glob(
        include = ["manifest-merger/src/test/java/**"],
        exclude = [
            "manifest-merger/src/test/java/**/*.java",
            "manifest-merger/src/test/java/**/*.kt",
        ],
    ),
    tags = ["no_test_windows"],  # b/77288863
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.manifest-merger",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
    ],
)

maven_library(
    name = "tools.apksig",
    srcs = [],
    bundled_deps = [
        "//tools/apksig:apksig-all",
    ],
    coordinates = "com.android.tools.build:apksig:" + BUILD_VERSION,
    description = "Library for signing APKs and for checking that APK signatures verify on Android",
    notice = "//tools/apksig:LICENSE",
    visibility = ["//visibility:public"],
)

# The Gradle APIs to build against and run against.
GRADLE_VERSION = "7.5"

filegroup(
    name = "gradle-distrib",
    srcs = ["//tools/external/gradle:gradle-distrib-" + GRADLE_VERSION],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gradle-distrib-7.5",
    srcs = ["//tools/external/gradle:gradle-distrib-7.5"],
    visibility = ["//tools/adt/idea/old-agp-tests:__pkg__"],
)

filegroup(
    name = "gradle-distrib-7.4",
    srcs = ["//tools/external/gradle:gradle-distrib-7.4"],
    visibility = ["//tools/adt/idea/old-agp-tests:__pkg__"],
)

filegroup(
    name = "gradle-distrib-7.3.3",
    srcs = ["//tools/external/gradle:gradle-distrib-7.3.3"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gradle-distrib-7.2",
    srcs = ["//tools/external/gradle:gradle-distrib-7.2"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gradle-distrib-7.0.2",
    srcs = ["//tools/external/gradle:gradle-distrib-7.0.2"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gradle-distrib-6.7.1",
    srcs = ["//tools/external/gradle:gradle-distrib-6.7.1"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gradle-distrib-6.5",
    srcs = ["//tools/external/gradle:gradle-distrib-6.5"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gradle-distrib-6.1.1",
    srcs = ["//tools/external/gradle:gradle-distrib-6.1.1"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gradle-distrib-5.5",
    srcs = ["//tools/external/gradle:gradle-distrib-5.5"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "gradle-distrib-5.3.1",
    srcs = ["//tools/external/gradle:gradle-distrib-5.3.1"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "gradle-tooling-api",
    visibility = ["//tools/base/bazel:__subpackages__"],
    exports = ["@maven//:org.gradle.gradle-tooling-api"],
    runtime_deps = ["@maven//:org.slf4j.slf4j-api"],
)

gradle_build(
    name = "gradle_api_jar",
    build_file = "extract-gradle-api/build.gradle",
    data = [":gradle-distrib"],
    output_file = "gradle-api.jar",
    # Note that the Gradle API (the output of this Gradle build) consists of multiple jars:
    #   - gradle-api-<version>.jar
    #   - gradle-installation-beacon-<version>.jar
    #   - groovy jars
    #   - kotlin-stdlib jars
    #   - etc.
    # Currently, we extract only the "thin" gradle-api-<version>.jar, and therefore we'll have to
    # add the other jars manually as needed.
    # Fixing this properly requires further work (there was a failed attempt to create a fat jar at
    # change Ia6c40546).
    output_file_source = "gradle-api-" + GRADLE_VERSION + ".jar",
    tasks = [":copyGradleApiJars"],
)

java_import(
    name = "gradle-api_neverlink",
    jars = [":gradle-api.jar"],
    neverlink = 1,
    visibility = ["//visibility:public"],
)

# Used for tests only.
java_import(
    name = "gradle-api",
    jars = [":gradle-api.jar"],
    visibility = ["//visibility:public"],
)

# Latest Kotlin Gradle plugin dependencies
# DO NOT MIX different versions in this list
KGP_LATEST = [
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.20-Beta",
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.20-Beta",
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.7.20-Beta",
    "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.7.20-Beta",
    "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.7.20-Beta",
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.7.20-Beta",
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.20-Beta",
]

# Kotlin Gradle plugin dependencies for projects using Compose (these are typically used when the
# projects can't be upgraded to the latest version of the Kotlin Gradle plugin as there isn't a
# compatible Compose version yet).
# DO NOT MIX different versions in this list.
KGP_FOR_COMPOSE = [
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.7.0",
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api_1.7.0",
    "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.7.0",
    "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.7.0",
    "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.7.0",
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.7.0",
    "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.7.0",
]

# m2 repository used by performance tests
maven_repository(
    name = "performance_test_repo",
    artifacts = KGP_LATEST + KGP_FOR_COMPOSE + [
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.6.10",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.6.10",
        "@maven//:org.jetbrains.kotlin.kotlin-script-runtime_1.6.10",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect_1.6.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk7_1.6.10",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8_1.6.10",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-android_1.3.6",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-common_1.3.6",
        "@maven//:androidx.activity.activity-compose_1.3.1",
        "@maven//:androidx.compose.compiler.compiler_1.2.0",
        "@maven//:androidx.compose.material.material_1.2.0",
        "@maven//:androidx.compose.animation.animation_1.2.0",
        "@maven//:androidx.compose.foundation.foundation_1.2.0",
        "@maven//:androidx.compose.material.material-icons-extended_1.2.0",
        "@maven//:androidx.compose.ui.ui-tooling_1.2.0",
        "@maven//:androidx.compose.runtime.runtime-livedata_1.2.0",
        "@maven//:androidx.appcompat.appcompat_1.3.0-beta01",
        "@maven//:androidx.lifecycle.lifecycle-livedata-ktx_2.2.0",
        "@maven//:androidx.lifecycle.lifecycle-common-java8_2.4.0",
    ],
    visibility = ["//prebuilts/studio/buildbenchmarks:__subpackages__"],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
        "manifest-merger/src/main/**/*.java",
        "manifest-merger/src/main/**/*.kt",
        "manifest-merger/src/main/resources/**",
    ]) + [
        "manifest-merger/build.gradle",
        "manifest-merger/NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)

# keep sorted
ANDROID_GRADLE_PLUGIN_SOURCE_FILES = [
    ":agp_gradle_build_files",
    "//prebuilts/tools/common/aapt/protos:agp_gradle_build_files",
    "//tools:agp_gradle_build_files",
    "//tools/base/build-system/aapt2:agp_gradle_build_files",
    "//tools/base/build-system/aapt2-proto:agp_gradle_build_files",
    "//tools/base/build-system/aaptcompiler:agp_gradle_build_files",
    "//tools/base/build-system/builder:agp_gradle_build_files",
    "//tools/base/build-system/builder-model:agp_gradle_build_files",
    "//tools/base/build-system/builder-test-api:agp_gradle_build_files",
    "//tools/base/build-system/gradle-api:agp_gradle_build_files",
    "//tools/base/build-system/gradle-core:agp_gradle_build_files",
    "//tools/base/build-system/gradle-settings:agp_gradle_build_files",
    "//tools/base/build-system/gradle-settings-api:agp_gradle_build_files",
    "//tools/base/build-system/profile:agp_gradle_build_files",
    "//tools/base/mlkit-common:agp_gradle_build_files",
    "//tools/base/profgen/profgen:agp_gradle_build_files",
    "//tools/buildSrc:agp_gradle_build_files",
    "//tools/buildSrc/base:agp_gradle_build_files",
]

gradle_build(
    name = "android_gradle_plugin",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = ["//prebuilts/studio/jdk:jdk11"] + ANDROID_GRADLE_PLUGIN_SOURCE_FILES,
    gradle_properties = {
        "hybrid-build-embedded-in-bazel": "true",
        "release": "true",
    },
    output_file = "android_gradle_plugin.zip",
    output_file_source = "dist/repo.zip",
    repos = [
        "//tools/base:agp_dependencies",
        "//tools/base:agp_artifacts",
    ],
    tasks = [":zipPlugin"],
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "android_gradle_plugin_runtime_dependencies",
    # keep sorted
    artifacts = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/analytics-library/crash:tools.analytics-crash",
        "//tools/analytics-library/protos/src/main/proto:proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/apkzlib",
        "//tools/base/annotations:annotations",
        "//tools/base/build-system:tools.apksig",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/device_validator:tools.dvlib",
        "//tools/base/emulator/proto",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/lint:tools.lint-gradle",
        "//tools/base/lint:tools.lint-model",
        "//tools/base/lint/cli",
        "//tools/base/lint/libs/lint-tests",
        "//tools/base/lint/libs/lint-typedef-remover",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/signflinger",
        "//tools/base/utp/android-device-provider-ddmlib-proto",
        "//tools/base/utp/android-device-provider-gradle-proto",
        "//tools/base/utp/android-test-plugin-host-additional-test-output-proto",
        "//tools/base/utp/android-test-plugin-host-coverage-proto",
        "//tools/base/utp/android-test-plugin-host-device-info-proto",
        "//tools/base/utp/android-test-plugin-host-retention-proto",
        "//tools/base/utp/android-test-plugin-result-listener-gradle-proto",
        "//tools/base/zipflinger",
        "//tools/data-binding:tools.baseLibrary",
        "//tools/data-binding:tools.baseLibrarySupport",
        "//tools/data-binding:tools.compiler",
        "//tools/data-binding:tools.compilerCommon",
        "@maven//:com.android.tools.build.bundletool",
        "@maven//:com.android.tools.build.jetifier.jetifier-processor",
        "@maven//:com.android.tools.build.transform-api",
        "@maven//:com.github.javaparser.javaparser-core",
        "@maven//:com.google.apis.google-api-services-storage",
        "@maven//:com.google.crypto.tink.tink",
        "@maven//:com.google.guava.guava-testlib",
        "@maven//:com.google.guava.guava_23.0",
        "@maven//:com.google.jimfs.jimfs",
        "@maven//:com.google.protobuf.protobuf-gradle-plugin",
        "@maven//:com.google.protobuf.protoc_3.17.2",
        "@maven//:com.google.testing.platform.core-proto",
        "@maven//:com.google.truth.extensions.truth-java8-extension",
        "@maven//:com.googlecode.jarjar.jarjar",
        "@maven//:com.googlecode.juniversalchardet.juniversalchardet",
        "@maven//:com.squareup.javapoet",
        "@maven//:com.squareup.javawriter",
        "@maven//:com.sun.activation.javax.activation",
        "@maven//:commons-io.commons-io",
        "@maven//:gradle.plugin.org.jetbrains.gradle.plugin.idea-ext.gradle-idea-ext",
        "@maven//:io.grpc.grpc-all",
        "@maven//:io.grpc.protoc-gen-grpc-java_1.39.0",
        "@maven//:io.netty.netty-all",
        "@maven//:javax.annotation.javax.annotation-api",
        "@maven//:net.java.dev.jna.jna-platform",
        "@maven//:net.sf.jopt-simple.jopt-simple",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:net.sf.proguard.proguard-gradle",
        "@maven//:nl.jqno.equalsverifier.equalsverifier",
        "@maven//:org.apache.commons.commons-compress",
        "@maven//:org.apache.httpcomponents.httpmime",
        "@maven//:org.apache.maven.maven-aether-provider",
        "@maven//:org.bouncycastle.bcpkix-jdk15on",
        "@maven//:org.codehaus.groovy.groovy-all",
        "@maven//:org.easymock.easymock",
        "@maven//:org.eclipse.aether.aether-connector-basic",
        "@maven//:org.eclipse.aether.aether-transport-file",
        "@maven//:org.eclipse.aether.aether-transport-http",
        "@maven//:org.glassfish.jaxb.jaxb-xjc",
        "@maven//:org.gradle.gradle-tooling-api",
        "@maven//:org.jacoco.org.jacoco.agent",
        "@maven//:org.jacoco.org.jacoco.report",
        "@maven//:org.jetbrains.dokka.dokka-android-gradle-plugin",
        "@maven//:org.jetbrains.dokka.dokka-core",
        "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin",
        "@maven//:org.jetbrains.kotlin.kotlin-compiler",
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin-api",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.smali.baksmali",
        "@maven//:org.tensorflow.tensorflow-lite-metadata",
        "@maven//:xerces.xercesImpl",
    ],
    visibility = ["//visibility:public"],
)

#  org.jetbrains.kotlin:kotlin-test-junit:1.5.3
maven_repository(
    name = "android_gradle_plugin_gradle_api_test_repository",
    # keep sorted
    artifacts = [
        "//tools/base/testutils:tools.testutils",
        "@maven//:org.jetbrains.kotlin.kotlin-test-junit_1.7.10",
    ],
)

gradle_test(
    name = "android_gradle_plugin_gradle_api_test",
    build_file = "//tools:agp_gradle_build_root_build_file",
    # keep sorted
    data = [
        ":agp_gradle_build_files",
        "//prebuilts/studio/jdk:jdk11",
        "//tools:agp_gradle_build_files",
        "//tools/base/build-system/builder-model:agp_gradle_build_files",
        "//tools/base/build-system/builder-test-api:agp_gradle_build_files",
        "//tools/base/build-system/gradle-api:agp_gradle_build_files",
        "//tools/base/build-system/gradle-api:agp_gradle_test_files",
        "//tools/buildSrc:agp_gradle_build_files",
        "//tools/buildSrc/base:agp_gradle_build_files",
    ],
    gradle_properties = {
        "hybrid-build-embedded-in-bazel": "true",
        "release": "true",
    },
    # keep sorted
    repos = [
        ":android_gradle_plugin_gradle_api_test_repository",
        "//tools/base:agp_artifacts",
        "//tools/base:agp_dependencies",
    ],
    tasks = [":base:gradle-api:test"],
    test_output_dir = "build/base/gradle-api/build/test-results/test",
    visibility = ["//visibility:public"],
)

gradle_build(
    name = "android_gradle_plugin_dackka_docs",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = [
        "//prebuilts/studio/jdk:jdk11",
        "//prebuilts/tools/common/dackka",
    ] + ANDROID_GRADLE_PLUGIN_SOURCE_FILES,
    gradle_properties = {
        "hybrid-build-embedded-in-bazel": "true",
        "release": "true",
    },
    output_file = "documentation.zip",
    output_file_source = "dist/documentation.zip",
    repos = [
        "//tools/base:agp_dependencies",
        "//tools/base:agp_artifacts",
    ],
    tags = ["no_windows"],  # TODO(b/202837927) Investigate getting Dackka to run on windows
    tasks = [":base:gradle-api:dackkaZip"],
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "metalava_tool",
    artifacts = [
        "@maven//:com.android.tools.metalava.metalava_1.0.0-alpha04",
    ],
)

gradle_build(
    name = "android_gradle_plugin_metalava_api",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = [
        "//prebuilts/studio/jdk:jdk11",
        "//prebuilts/tools/common/dackka",
    ] + ANDROID_GRADLE_PLUGIN_SOURCE_FILES,
    gradle_properties = {
        "hybrid-build-embedded-in-bazel": "true",
        "release": "true",
    },
    output_file = "apis.zip",
    output_file_source = "dist/apis.zip",
    repos = [
        "//tools/base:agp_dependencies",
        "//tools/base:agp_artifacts",
        ":metalava_tool",
    ],
    tags = ["no_windows"],  # TODO(b/202837927) Investigate getting metalava to run on windows
    tasks = [":base:gradle-api:distMetalavaApiZip"],
    visibility = ["//visibility:public"],
)

gradle_test(
    name = "android_gradle_plugin_gradle_api_metalava_test",
    build_file = "//tools:agp_gradle_build_root_build_file",
    # keep sorted
    data = [
        ":agp_gradle_build_files",
        "//prebuilts/studio/jdk:jdk11",
        "//tools:agp_gradle_build_files",
        "//tools/base/build-system/builder-model:agp_gradle_build_files",
        "//tools/base/build-system/builder-test-api:agp_gradle_build_files",
        "//tools/base/build-system/gradle-api:agp_gradle_build_files",
        "//tools/base/build-system/gradle-api:agp_gradle_metalava_test_files",
        "//tools/buildSrc:agp_gradle_build_files",
        "//tools/buildSrc/base:agp_gradle_build_files",
    ],
    gradle_properties = {
        "hybrid-build-embedded-in-bazel": "true",
        "release": "true",
    },
    # keep sorted
    repos = [
        ":android_gradle_plugin_gradle_api_test_repository",
        ":metalava_tool",
        "//tools/base:agp_artifacts",
        "//tools/base:agp_dependencies",
    ],
    tasks = [":base:gradle-api:metalavaTest"],
    test_output_dir = "build/base/gradle-api/build/test-results/metalavaTest",
    visibility = ["//visibility:public"],
)

# This should be kept in sync with
# tools/base/testutils/src/main/java/com/android/testutils/TestUtils.ANDROID_PLATFORM_FOR_AGP_UNIT_TESTS
filegroup(
    name = "android_platform_for_tests",
    srcs = ["//prebuilts/studio/sdk:platforms/android-33"],
    visibility = [":__subpackages__"],
)

filegroup(
    name = "android_platform_for_performance_tests",
    srcs = ["//prebuilts/studio/sdk:platforms/android-33_build_only"],
    visibility = [
        "//prebuilts/studio/buildbenchmarks:__pkg__",
    ],
)
