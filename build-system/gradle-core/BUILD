load("//tools/base/bazel:bazel.bzl", "java_proto_library")
load("//tools/base/bazel:antlr.bzl", "antlr_sources")

antlr_sources(
    name = "proguard-parser",
    srcs = glob(["src/main/antlr/**"]),
)

java_library(
    name = "gradle-core",
    srcs = glob([
        "src/main/java/**/*.java",
        "src/main/generated/**/*.java",
        "src/fromGradle/java/**/*.java",
    ]) + [":proguard-parser"],
    javacopts = ["-extra_checks:off"],
    resources = glob([
        "src/main/resources/**",
        "src/fromGradle/resources/**",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library:analytics-protos",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-api",
        "//tools/base/build-system/gradle-core/src/main/proto",  # TODO: move this to this package?
        "//tools/base/build-system/profile",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/lint/cli",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
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
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/data-binding:tools.compilerCommon",
        "//tools/external/gradle:gradle-api.jar",
    ],
)

java_test(
    name = "tests",
    # TODO: Make IR tests work.
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = ["src/test/java/com/android/build/gradle/internal/incremental/**"],
    ),
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    javacopts = ["-extra_checks:off"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    tags = ["manual"],  # TODO: Make these test pass.
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":gradle-core",
        "//tools/analytics-library:analytics-protos",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-api",
        "//tools/base/build-system/gradle-core/src/main/proto",  # TODO: move this to this package?
        "//tools/base/build-system/profile",
        "//tools/base/common:tools.common",
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
        "//tools/base/third_party:org.eclipse.jdt.core.compiler_ecj",
        "//tools/base/third_party:org.jsoup_jsoup",
        "//tools/base/third_party:org.mockito_mockito-all",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-debug-all",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
        "//tools/data-binding:tools.compilerCommon",
        "//tools/external/gradle:gradle-api.jar",
    ],
)
