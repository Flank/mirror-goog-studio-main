load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")
load("//tools/base/common:version.bzl", "BASE_VERSION")

maven_library(
    name = "cli",
    # TODO: move resources out of java?
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        ":groovy_neverlink",
    ],
    coordinates = "com.android.tools.lint:lint:" + BASE_VERSION,
    description = "Lint tools. Both a Command line tool and a library to add lint features to other tools",
    lint_baseline = "lint_baseline.xml",
    module_name = "lint-cli",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/lint/cli",
    resources = glob(
        include = ["src/main/java/**"],
        exclude = [
            "src/main/java/**/*.java",
            "src/main/java/**/*.kt",
        ],
    ),
    visibility = ["//visibility:public"],
    exports = [
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
    ],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:com.google.guava.guava",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

java_library(
    name = "groovy_neverlink",
    neverlink = 1,
    exports = ["@maven//:org.codehaus.groovy.groovy"],
)

sdk_java_binary(
    name = "lint",
    default_jvm_opts = {
        "linux": "-Xmx1024m -Dcom.android.tools.lint.bindir=$APP_HOME",
        "win": "-Xmx1024m -Dcom.android.tools.lint.bindir=%~dp0\\..",
        "mac": "-Xmx1024m -Dcom.android.tools.lint.bindir=$APP_HOME",
    },
    main_class = "com.android.tools.lint.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [":cli"],
)

load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint.cli",
    iml_files = ["android.sdktools.lint.cli.iml"],
    test_srcs = ["src/main/java"],
    # There are no tests here, this just just test-scope as it is not bundled in Studio
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/lint/cli:lint-cli-psi-prebuilts"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/lint/cli:lint-cli-psi-prebuilts[test]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module, test]",
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module, test]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module, test]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "lint-cli-psi-prebuilts",
    # do not sort: must match IML order
    jars = [
        "//prebuilts/tools/common/lint-psi/intellij-core:lint-cli-psi-prebuilts_files",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler:lint-cli-psi-prebuilts_files",
        "//prebuilts/tools/common/lint-psi/uast:lint-cli-psi-prebuilts_files",
    ],
    visibility = ["//visibility:public"],
)
