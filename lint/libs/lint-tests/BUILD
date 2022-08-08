load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "lint-tests",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-tests:" + BASE_VERSION,
    description = "API to build lint check tests",
    module_name = "lint-tests",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-tests",
    visibility = ["//visibility:public"],
    exports = [
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint/cli",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:junit.junit",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.codehaus.groovy.groovy",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-tree",
    ],
)

kotlin_test(
    name = "tests",
    timeout = "long",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    data = [
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Djdk.attach.allowAttachSelf=true",
        # TODO: Inject the cache directory into tests.
        "-Duser.home=/tmp",
    ],
    lint_baseline = "lint_baseline.xml",
    resources = glob(["src/test/resources/**"]),
    shard_count = 2,
    tags = [
        "slow",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":lint-tests",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/testing:tools.analytics-testing",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/apkparser/analyzer:tools.analyzer",
        "//tools/base/bazel:langtools",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/common:tools.fixtures",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/lint:tools.lint-model",
        "//tools/base/lint/cli",
        "//tools/base/repository:tools.repository",
        "//tools/base/repository:tools.testlib",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.codehaus.groovy.groovy",
        "@maven//:org.jetbrains.annotations",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.mockito.mockito-core",
        "@maven//:org.ow2.asm.asm-tree",
    ],
)

load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.testinfrastructure",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.lint.testinfrastructure.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-model[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module]",
        "//prebuilts/studio/intellij-sdk:studio-sdk",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.tests",
    iml_files = ["android.sdktools.lint.tests.iml"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    # The cannonical test target for this is :tests (//tools/base/lint/libs/lint-tests:tests)
    # as that runs with the correct dependencies. This iml_module is only defined for development
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/tools/common/m2:groovy-3.0.9",
        "//tools/base/lint/cli:studio.android.sdktools.lint.cli[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module, test]",
        "//tools/base/lint/libs/lint-tests:studio.android.sdktools.lint.testinfrastructure[module, test]",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:truth-java8-extension[test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/common:studio.android.sdktools.common.testfixtures[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
        "//tools/base/lint/libs/lint-tests:liblint-checks-proto",
        "//tools/adt/idea/.idea/libraries:kotlin-test[test]",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "liblint-checks-proto",
    jars = ["//tools/base/lint:liblint-checks-proto.lib.jar"],
    visibility = ["//visibility:public"],
)
