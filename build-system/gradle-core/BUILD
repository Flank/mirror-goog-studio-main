load("//tools/base/bazel:antlr.bzl", "antlr_sources")
load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

antlr_sources(
    name = "proguard-parser",
    srcs = glob(["src/main/antlr/**"]),
)

fileset(
    name = "resources",
    srcs = glob([
        "src/main/resources/**",
        "src/fromGradle/resources/**",
    ]) + [
        # TODO: this jar needs to contain classes from instant-run-runtime as well.
        "//tools/base/instant-run/instant-run-server:libinstant-run-server.jar",
    ],
    mappings = {
        "//tools/base/instant-run/instant-run-server:libinstant-run-server.jar": "instant-run/instant-run-server.jar",
        "src/main/resources/": "",
        "src/fromGradle/resources/": "",
    },
)

kotlin_library(
    name = "gradle-core",
    srcs = [
        "src/fromGradle/java",
        "src/main/java",
    ],
    bundled_deps = [
        "//tools/base/build-system/gradle-core/src/main/proto",
        "//tools/base/build-system/instant-run-instrumentation",
    ],
    exclusions = {
        "org.jacoco:org.jacoco.core": ["org.ow2.asm:asm-debug-all"],
        "org.jacoco:org.jacoco.report": ["org.ow2.asm:asm-debug-all"],
    },
    java_srcs = [":proguard-parser"],
    plugins = [
        "//tools/base:auto-value-plugin",
    ],
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/gradle-core",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/intellij-core:intellij-core-171.4249.32",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/base:com.google.auto.value_auto-value_neverlink",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/lint/cli",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.android.tools.build_transform-api",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.intellij_annotations",
        "//tools/base/third_party:commons-io_commons-io",  # TODO: remove?
        "//tools/base/third_party:net.sf.jopt-simple_jopt-simple",
        "//tools/base/third_party:net.sf.proguard_proguard-gradle",
        "//tools/base/third_party:org.antlr_antlr-runtime",
        "//tools/base/third_party:org.apache.commons_commons-compress",  # TODO: remove?
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.eclipse.jdt.core.compiler_ecj",
        "//tools/base/third_party:org.jacoco_org.jacoco.core",
        "//tools/base/third_party:org.jacoco_org.jacoco.report",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-analysis",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
        "//tools/base/third_party:org.ow2.asm_asm-util",
        "//tools/data-binding:tools.compilerCommon",
    ],
)

maven_pom(
    name = "pom",
    artifact = "gradle-core",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

kotlin_test(
    name = "tests",
    srcs = ["src/test/java"],
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    # TODO: Remove the blacklist, once NDK is checked in.
    test_class = "com.android.build.gradle.internal.GradleCoreBazelSuite",
    deps = [
        ":gradle-core",
        "//tools/analytics-library/protos:analytics-protos",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-api",
        "//tools/base/build-system/gradle-core/src/main/proto",  # TODO: move this to this package?
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/repository:tools.repository",
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
        "//tools/base/third_party:org.eclipse.jdt.core.compiler_ecj",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jsoup_jsoup",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
        "//tools/base/third_party:org.ow2.asm_asm-util",
        "//tools/data-binding:tools.compilerCommon",
    ],
)
