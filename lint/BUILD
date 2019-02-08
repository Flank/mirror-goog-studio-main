load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-api",
    srcs = ["libs/lint-api/src/main/java"],
    iml_files = ["libs/lint-api/android.sdktools.lint-api.iml"],
    lint_baseline = "libs/lint-api/lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/idea/java/java-psi-api:intellij.java.psi",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger",
        "//tools/idea/uast/uast-common:intellij.platform.uast",
        "//tools/idea/uast/uast-java:intellij.java.uast",
        "//tools/idea/.idea/libraries:kotlin-plugin",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
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
    srcs = ["libs/lint-api/src/main/java"],
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
    resources = ["libs/lint-checks/src/main/resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/lint:studio.android.sdktools.lint-api"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)

kotlin_library(
    name = "tools.lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    pom = "lint-checks.pom",
    resource_strip_prefix = "tools/base/lint/libs/lint-checks/src/main/resources",
    resources = glob(["libs/lint-checks/src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-api",
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
    srcs = ["libs/lint-gradle-api/src/main/java"],
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
    srcs = ["libs/lint-gradle/src/main/java"],
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
        ":tools.lint-gradle-api",
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
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
        "//tools/base/lint/cli",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.jetbrains_annotations",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-reflect",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
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
    srcs = ["libs/lint-gradle/src/test/java"],
    data = [
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/adt/idea/android/annotations",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Duser.home=/tmp",
    ],
    tags = ["no_test_windows"],  # b/73306170
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.lint-gradle",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-all/1.9.5:jar",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint:tools.lint-api",
        "//tools/base/lint:tools.lint-checks",
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
    srcs = ["studio-checks/src/main/java"],
    visibility = ["//visibility:public"],
    deps = [
        ":tools.lint-api",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
    ],
)

kotlin_test(
    name = "studio-checks-tests",
    srcs = ["studio-checks/src/test/java"],
    jvm_flags = [
        "-Dtest.suite.jar=studio-checks-tests.jar",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    visibility = ["//visibility:public"],
    deps = [
        ":studio-checks",
        ":tools.lint-api",
        "//tools/base/lint/libs/lint-tests",
        "//tools/base/third_party:junit_junit",
    ],
)
