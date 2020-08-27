load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:utils.bzl", "replace_manifest")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-model",
    srcs = ["libs/lint-model/src/main/java"],
    iml_files = ["libs/lint-model/android.sdktools.lint-model.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/idea/.idea/libraries:javax.activation"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:kxml2",
    ],
)

kotlin_library(
    name = "tools.lint-model",
    srcs = glob([
        "libs/lint-model/src/main/java/**/*.kt",
        "libs/lint-model/src/main/java/**/*.java",
    ]),
    # http://b/166582569
    api_version = "1.3",
    module_name = "lint-model",
    pom = "lint-model.pom",
    resource_strip_prefix = "tools/base/lint/libs/lint-model",
    resources = glob(
        include = ["libs/lint-model/src/main/java/**"],
        exclude = [
            "libs/lint-model/src/main/java/**/*.java",
            "libs/lint-model/src/main/java/**/*.kt",
        ],
    ) + ["libs/lint-model/NOTICE"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

maven_pom(
    name = "lint-model.pom",
    artifact = "lint-model",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-api",
    srcs = ["libs/lint-api/src/main/java"],
    iml_files = ["libs/lint-api/android.sdktools.lint-api.iml"],
    lint_baseline = "libs/lint-api/empty_baseline.xml",
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
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/lint:studio.android.sdktools.lint-model",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/idea/java/java-psi-api:intellij.java.psi",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger",
        "//tools/idea/uast/uast-common:intellij.platform.uast",
        "//tools/idea/uast/uast-java:intellij.java.uast",
        "//tools/idea/.idea/libraries:kotlin-plugin",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/workspaceModel/ide:intellij.platform.workspaceModel.ide",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.annotation-api",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//prebuilts/tools/common/m2/repository/com/jetbrains/intellij/documentation/tips-intellij-idea-community/202.13:jar",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/lint:studio.android.sdktools.lint-model[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:kotlin-reflect",
        "//tools/idea/uast/uast-common:intellij.platform.uast[module]",
        "//tools/idea/uast/uast-java:intellij.java.uast[module]",
        "//tools/idea/.idea/libraries:kotlin-plugin",
    ],
)

kotlin_library(
    name = "tools.lint-api",
    # TODO: move resources out of java?
    srcs = glob([
        "libs/lint-api/src/main/java/**/*.kt",
        "libs/lint-api/src/main/java/**/*.java",
    ]),
    # http://b/166582569
    api_version = "1.3",
    module_name = "lint-api",
    pom = "lint-api.pom",
    resource_strip_prefix = "tools/base/lint/libs/lint-api",
    resources = glob(
        include = ["libs/lint-api/src/main/java/**"],
        exclude = [
            "libs/lint-api/src/main/java/**/*.java",
            "libs/lint-api/src/main/java/**/*.kt",
        ],
    ) + ["libs/lint-api/NOTICE"],
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.trove4j_trove4j",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)

maven_pom(
    name = "lint-api.pom",
    artifact = "lint-api",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    iml_files = ["libs/lint-checks/android.sdktools.lint-checks.iml"],
    lint_baseline = "libs/lint-checks/lint_baseline.xml",
    resources = ["libs/lint-checks/src/main/resources"],
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
    visibility = ["//visibility:public"],
    exports = ["//tools/base/lint:studio.android.sdktools.lint-api"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:javax.activation",
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/statistics/devkit:intellij.platform.statistics.devkit",
        "//tools/idea/platform/workspaceModel/ide:intellij.platform.workspaceModel.ide",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/idea/.idea/libraries:javax.annotation-api",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-jsr223",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-json",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-templates",
        "//tools/idea/.idea/libraries:org.codehaus.groovy_groovy-xml",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//prebuilts/tools/common/m2/repository/com/jetbrains/intellij/documentation/tips-intellij-idea-community/202.13:jar",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)

kotlin_library(
    name = "tools.lint-checks",
    srcs = glob([
        "libs/lint-checks/src/main/java/**/*.kt",
        "libs/lint-checks/src/main/java/**/*.java",
    ]),
    # http://b/166582569
    api_version = "1.3",
    lint_baseline = "libs/lint-checks/lint_baseline.xml",
    module_name = "lint-checks",
    pom = "lint-checks.pom",
    resource_strip_prefix = "tools/base/lint/libs/lint-checks/src/main/resources",
    resources = glob(["libs/lint-checks/src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-api",
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.trove4j_trove4j",
        "//tools/base/third_party:org.ow2.asm_asm-analysis",
    ],
)

maven_pom(
    name = "lint-checks.pom",
    artifact = "lint-checks",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)

kotlin_library(
    name = "tools.lint-gradle-api",
    # TODO: move resources out of java?
    srcs = glob([
        "libs/lint-gradle-api/src/main/java/**/*.kt",
        "libs/lint-gradle-api/src/main/java/**/*.java",
    ]),
    # http://b/166582569
    api_version = "1.3",
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    module_name = "lint-gradle-api",
    pom = "lint-gradle-api.pom",
    resource_strip_prefix = "tools/base/lint/libs/lint-gradle-api",
    resources = glob(
        include = ["libs/lint-gradle-api/src/main/java/**"],
        exclude = [
            "libs/lint-gradle-api/src/main/java/**/*.java",
            "libs/lint-gradle-api/src/main/java/**/*.kt",
        ],
    ) + ["libs/lint-gradle-api/NOTICE"],
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-model",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system/builder-model",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

maven_pom(
    name = "lint-gradle-api.pom",
    artifact = "lint-gradle-api",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)

kotlin_library(
    name = "tools.lint-gradle",
    # TODO: move resources out of java?
    srcs = glob([
        "libs/lint-gradle/src/main/java/**/*.kt",
        "libs/lint-gradle/src/main/java/**/*.java",
    ]),
    # http://b/166582569
    api_version = "1.3",
    lint_baseline = "lint_baseline_gradle.xml",
    module_name = "lint-gradle",
    pom = "lint-gradle.pom",
    resource_strip_prefix = "tools/base/lint/libs/lint-gradle",
    resources = glob(
        include = ["libs/lint-gradle/src/main/java/**"],
        exclude = [
            "libs/lint-gradle/src/main/java/**/*.java",
            "libs/lint-gradle/src/main/java/**/*.kt",
        ],
    ) + ["libs/lint-gradle/NOTICE"],
    visibility = ["//visibility:public"],
    deps = [
        # NOTE NOTE NOTE - before changing this, note that lint dependencies
        # must also be reflected in [ReflectiveLintRunner#computeUrls] as well
        # so update both in sync
        ":tools.lint-api",
        ":tools.lint-checks",
        ":tools.lint-gradle-api",
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system/gradle-api",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint/cli",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.jetbrains_annotations",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/zipflinger",
    ],
)

maven_pom(
    name = "lint-gradle.pom",
    artifact = "lint-gradle",
    group = "com.android.tools.lint",
    source = "//tools/buildSrc/base:base_version",
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "libs/lint-gradle/src/test/java/**/*.kt",
        "libs/lint-gradle/src/test/java/**/*.java",
    ]),
    data = [
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Duser.home=/tmp",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.lint-api",
        ":tools.lint-checks",
        ":tools.lint-gradle",
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-all/1.9.5:jar",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint/cli",
        "//tools/base/lint/libs/lint-tests",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:net.sf.kxml_kxml2",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.trove4j_trove4j",
        "//tools/base/third_party:org.jetbrains_annotations",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)

kotlin_library(
    name = "studio-checks",
    srcs = glob([
        "studio-checks/src/main/java/**/*.kt",
        "studio-checks/src/main/java/**/*.java",
    ]),
    lint_baseline = "studio-checks/empty_baseline.xml",
    lint_classpath = ["//tools/base/lint/cli"],
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-api",
        ":tools.lint-checks",
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
    ],
)

replace_manifest(
    name = "studio-checks.lint-rules",
    manifest = "studio-checks/MANIFEST.MF",
    original_jar = ":studio-checks",
    visibility = ["//visibility:public"],
)

kotlin_test(
    name = "studio-checks-tests",
    srcs = glob([
        "studio-checks/src/test/java/**/*.kt",
        "studio-checks/src/test/java/**/*.java",
    ]),
    jvm_flags = [
        "-Dtest.suite.jar=studio-checks-tests.jar",
    ],
    tags = ["no_windows"],
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    deps = [
        ":studio-checks",
        ":tools.lint-api",
        ":tools.lint-checks",
        "//tools/base/lint/libs/lint-tests",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

exports_files(
    srcs = ["studio-checks/empty_baseline.xml"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "studio.android.sdktools.lint-model",
    srcs = ["libs/lint-model/src/main/java"],
    iml_files = ["libs/lint-model/android.sdktools.lint-model.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.kxml2",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "studio.android.sdktools.lint-api",
    srcs = ["libs/lint-api/src/main/java"],
    iml_files = ["libs/lint-api/android.sdktools.lint-api.iml"],
    lint_baseline = "libs/lint-api/empty_baseline.xml",
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/adt/idea/.idea/libraries:unb.asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/base/lint:studio.android.sdktools.lint-model",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:unb.asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/base/lint:studio.android.sdktools.lint-model[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "studio.android.sdktools.lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    iml_files = ["libs/lint-checks/android.sdktools.lint-checks.iml"],
    lint_baseline = "libs/lint-checks/lint_baseline.xml",
    project = "unb",
    resources = ["libs/lint-checks/src/main/resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/lint:studio.android.sdktools.lint-api"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-java",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
    ],
)
