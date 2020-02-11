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
    srcs = [
        "src/fromGradle/java",
        "src/main/java",
    ],
    bundled_deps = [
        ":proto",
    ],
    lint_baseline = "lint_baseline.xml",
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
        "//tools/base/lint:tools.lint-gradle-api",
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
        "//tools/base/third_party:com.android.tools.build.jetifier_jetifier-core",
        "//tools/base/third_party:com.android.tools.build.jetifier_jetifier-processor",
        "//tools/base/third_party:com.android.tools.build_bundletool",
        "//tools/base/third_party:com.android.tools.build_transform-api",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.google.protobuf_protobuf-java-util",
        "//tools/base/third_party:com.sun.istack_istack-commons-runtime",
        "//tools/base/third_party:commons-io_commons-io",  # TODO: remove?
        "//tools/base/third_party:net.sf.jopt-simple_jopt-simple",
        "//tools/base/third_party:net.sf.proguard_proguard-gradle",
        "//tools/base/third_party:org.apache.commons_commons-compress",  # TODO: remove?
        "//tools/base/third_party:org.apache.httpcomponents_httpmime",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains_annotations",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-analysis",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
        "//tools/base/third_party:org.ow2.asm_asm-util",
        "//tools/base/zipflinger",
        "//tools/data-binding:tools.compilerCommon",
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

kotlin_test(
    name = "tests",
    size = "large",
    timeout = "long",
    srcs = ["src/test/java"],
    coverage = True,
    data = [
        "//prebuilts/studio/sdk:add-ons/addon-google_apis-google-latest",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-24",
        "//prebuilts/studio/sdk:platforms/android-27",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    friends = [":gradle-core"],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        # TODO: Make it easier to configure AndroidLocation per project.
        "-DANDROID_SDK_HOME=/tmp/android_sdk_home",
    ],
    resources = glob(["src/test/resources/**"]),
    tags = [
        "slow",
    ],
    # TODO: Remove the blacklist, once NDK is checked in.
    test_class = "com.android.build.gradle.internal.GradleCoreBazelSuite",
    # Specify gradle-api jar first, as kotlin-daemon-client contains older net.rubygrapefruit.platform classes
    deps = ["//tools/base/build-system:gradle-api"] + [
        ":gradle-core",
        "//prebuilts/tools/common/m2/repository/com/android/tools/desugar_jdk_libs/1.0.4:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/desugar_jdk_libs_configuration/0.11.0:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-reflect/1.3.72:jar",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-gradle-api",
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
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.jacoco_org.jacoco.core",
        "//tools/base/third_party:org.jacoco_org.jacoco.report",
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
