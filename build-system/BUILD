load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:gradle.bzl", "gradle_build")
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
GRADLE_VERSION = "7.3-rc-1"

filegroup(
    name = "gradle-distrib",
    srcs = ["//tools/external/gradle:gradle-distrib-" + GRADLE_VERSION],
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

# repos for the gradle plugin and the offline repo packaged inside Studio

# m2 repository to run the Gradle plugin minus the data-binding dependency.
# When running the gradle plugin, prefer this one to gradle_plugin_repo if you don't
# need data-binding. Data-binding requires much more work/dependencies, as it builds
# the data-binding runtime library with the Gradle plugin.

GRADLE_PLUGIN_NO_DATABINDING_ARTIFACTS = [
    "//tools/base/build-system/aapt2:aapt2",
    "//tools/base/build-system/gradle-core:gradle-core",
    "//tools/base/lint:tools.lint-gradle",
    "@maven//:com.github.javaparser.javaparser-core",
    "@maven//:org.junit.junit-bom",  # Added because of http://b/202838125
]

maven_repository(
    name = "gradle_plugin_no_databinding_repo",
    artifacts = GRADLE_PLUGIN_NO_DATABINDING_ARTIFACTS,
    visibility = ["//visibility:public"],
)

# Full m2 repository to run the Gradle plugin.
# Only use if you need data-binding, otherwise use gradle_plugin_no_databinding_repo
GRADLE_PLUGIN_ARTIFACTS = GRADLE_PLUGIN_NO_DATABINDING_ARTIFACTS + [
    "//tools/data-binding:tools.compiler",
]

maven_repository(
    name = "gradle_plugin_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS,
    visibility = ["//visibility:public"],
)

# m2 repository used by performance tests
maven_repository(
    name = "performance_test_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS + [
        # TODO(b/201790375): Update Kotlin deps.
        "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.4.32",
        "@maven//:org.jetbrains.kotlin.kotlin-android-extensions-runtime_1.4.32",
        "@maven//:androidx.activity.activity-compose_1.3.0-alpha02",
        "@maven//:androidx.compose.compiler.compiler_1.0.0-beta07",
        "@maven//:androidx.compose.material.material_1.0.0-beta07",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-android_1.3.6",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core-common_1.3.6",
    ],
    visibility = ["//prebuilts/studio/buildbenchmarks:__subpackages__"],
)

# m2 repository packaged inside studio.
maven_repository(
    name = "studio_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS,
    visibility = ["//visibility:public"],
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
    "//tools/base:agp_artifacts.zip",
    "//tools/base/build-system/aapt2:agp_gradle_build_files",
    "//tools/base/build-system/aapt2-proto:agp_gradle_build_files",
    "//tools/base/build-system/aaptcompiler:agp_gradle_build_files",
    "//tools/base/build-system/builder:agp_gradle_build_files",
    "//tools/base/build-system/builder-model:agp_gradle_build_files",
    "//tools/base/build-system/builder-test-api:agp_gradle_build_files",
    "//tools/base/build-system/gradle-api:agp_gradle_build_files",
    "//tools/base/build-system/gradle-core:agp_gradle_build_files",
    "//tools/base/build-system/profile:agp_gradle_build_files",
    "//tools/base/fakeadbserver:agp_gradle_build_files",  # Only published, not a dependency of AGP
    "//tools/base/mlkit-common:agp_gradle_build_files",
    "//tools/base/profgen/profgen:agp_gradle_build_files",
    "//tools/base/sdk-common:agp_gradle_build_files",
    "//tools/base/sdk-common/aia-manifest-jar-generator:agp_gradle_build_files",
    "//tools/base/sdklib:agp_gradle_build_files",
    "//tools/base/signflinger:agp_gradle_build_files",
    "//tools/base/testutils:agp_gradle_build_files",  # Only published, not a dependency of AGP
    "//tools/base/utp/android-device-provider-ddmlib:agp_gradle_build_files",
    "//tools/base/utp/android-device-provider-ddmlib-proto:agp_gradle_build_files",
    "//tools/base/utp/android-device-provider-gradle:agp_gradle_build_files",
    "//tools/base/utp/android-device-provider-gradle-proto:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-additional-test-output:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-additional-test-output-proto:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-coverage:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-coverage-proto:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-device-info:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-device-info-proto:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-logcat:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-retention:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-host-retention-proto:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-result-listener-gradle:agp_gradle_build_files",
    "//tools/base/utp/android-test-plugin-result-listener-gradle-proto:agp_gradle_build_files",
    "//tools/base/zipflinger:agp_gradle_build_files",
    "//tools/buildSrc:agp_gradle_build_files",
    "//tools/buildSrc/base:agp_gradle_build_files",
    "//tools/data-binding:agp_gradle_build_files",
]

gradle_build(
    name = "android_gradle_plugin",
    build_file = "//tools:agp_gradle_build_root_build_file",
    data = ["//prebuilts/studio/jdk"] + ANDROID_GRADLE_PLUGIN_SOURCE_FILES,
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
