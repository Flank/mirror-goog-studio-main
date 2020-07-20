load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test", "coverage_java_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.analyzer",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.analyzer.iml"],
    lint_baseline = "lint_baseline.xml",
    test_resources = ["src/test/resources"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/groovy/jps-plugin:intellij.groovy.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/plugins/properties:intellij.properties",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/typeMigration:intellij.java.typeMigration",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/plugins/java-i18n:intellij.java.i18n",
        "//tools/idea/plugins/IntelliLang:intellij.java.langInjection",
        "//tools/idea/plugins/testng:intellij.testng",
        "//tools/idea/plugins/junit:intellij.junit",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/coverage:intellij.java.coverage",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/plugins/groovy/groovy-psi:intellij.groovy.psi",
        "//tools/idea/plugins/eclipse:intellij.eclipse",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/.idea/libraries:precompiled_jshell-frontend",
    ],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/lint:studio.android.sdktools.lint-checks",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/workspaceModel-ide:intellij.platform.workspaceModel.ide",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/.idea/libraries:javassist",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.annotation-api",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//prebuilts/tools/common/m2/repository/com/jetbrains/intellij/documentation/tips-intellij-idea-community/201.11:jar",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//prebuilts/tools/common/archive-patcher:explainer",
        "//prebuilts/tools/common/archive-patcher:generator",
        "//prebuilts/tools/common/archive-patcher:shared",
        "//tools/idea/.idea/libraries:dexlib2",
        "//tools/idea/.idea/libraries:baksmali",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jimfs[test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:studio.android.sdktools.lint-checks[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
    ],
)

coverage_java_library(
    name = "tools.analyzer",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/archive-patcher:explainer",
        "//prebuilts/tools/common/archive-patcher:generator",
        "//prebuilts/tools/common/archive-patcher:shared",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/common:tools.common",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.smali_baksmali",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.analyzer",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "unb.studio.android.sdktools.analyzer",
    srcs = ["src/main/java"],
    bundled_deps = [
        "//prebuilts/tools/common/archive-patcher:unb.explainer",
        "//prebuilts/tools/common/archive-patcher:unb.generator",
        "//prebuilts/tools/common/archive-patcher:unb.shared",
    ],
    iml_files = ["android.sdktools.analyzer.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = [
        "manual",
        "unb",
    ],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/lint:unb.studio.android.sdktools.lint-api",
        "//tools/base/lint:unb.studio.android.sdktools.lint-checks",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:unb.studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:unb.studio.android.sdktools.common[module]",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//prebuilts/tools/common/archive-patcher:unb.explainer",
        "//prebuilts/tools/common/archive-patcher:unb.generator",
        "//prebuilts/tools/common/archive-patcher:unb.shared",
        "//tools/adt/idea/.idea/libraries:unb.dexlib2",
        "//tools/adt/idea/.idea/libraries:unb.baksmali",
        "//tools/base/testutils:unb.studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/.idea/libraries:unb.jimfs[test]",
        "//tools/base/apkparser:unb.studio.android.sdktools.binary-resources[module]",
        "//tools/base/lint:unb.studio.android.sdktools.lint-api[module]",
        "//tools/base/lint:unb.studio.android.sdktools.lint-checks[module]",
        "//tools/base/sdk-common:unb.studio.android.sdktools.sdk-common[module]",
    ],
)
