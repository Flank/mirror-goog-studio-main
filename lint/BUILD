load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test", "maven_library")
load("//tools/base/bazel:utils.bzl", "replace_manifest")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-model",
    srcs = ["libs/lint-model/src/main/java"],
    iml_files = ["libs/lint-model/android.sdktools.lint-model.iml"],
    lint_baseline = "libs/lint-model/lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/build-system/builder-model:studio.android.sdktools.builder-model[module]",
        "//tools/adt/idea/.idea/libraries:kxml2",
    ],
)

maven_library(
    name = "tools.lint-model",
    srcs = glob([
        "libs/lint-model/src/main/java/**/*.kt",
        "libs/lint-model/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-model:" + BASE_VERSION,
    module_name = "lint-model",
    notice = "libs/lint-model/NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-model",
    resources = glob(
        include = ["libs/lint-model/src/main/java/**"],
        exclude = [
            "libs/lint-model/src/main/java/**/*.java",
            "libs/lint-model/src/main/java/**/*.kt",
        ],
    ),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-api",
    srcs = ["libs/lint-api/src/main/java"],
    iml_files = ["libs/lint-api/android.sdktools.lint-api.iml"],
    lint_baseline = "libs/lint-api/lint_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/base/lint:studio.android.sdktools.lint-model",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/adt/idea/.idea/libraries:asm-tools",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/lint:studio.android.sdktools.lint-model[module]",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/sdklib:studio.android.sdktools.sdklib[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api[module]",
        "//tools/base/build-system:studio.android.sdktools.manifest-merger[module]",
    ],
)

maven_library(
    name = "tools.lint-api",
    # TODO: move resources out of java?
    srcs = glob([
        "libs/lint-api/src/main/java/**/*.kt",
        "libs/lint-api/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-api:" + BASE_VERSION,
    module_name = "lint-api",
    notice = "libs/lint-api/NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-api",
    resources = glob(
        include = ["libs/lint-api/src/main/java/**"],
        exclude = [
            "libs/lint-api/src/main/java/**/*.java",
            "libs/lint-api/src/main/java/**/*.kt",
        ],
    ),
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
        "@maven//:com.google.guava.guava",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-tree",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.lint-checks",
    srcs = ["libs/lint-checks/src/main/java"],
    iml_files = ["libs/lint-checks/android.sdktools.lint-checks.iml"],
    lint_baseline = "libs/lint-checks/lint_baseline.xml",
    resources = ["libs/lint-checks/src/main/resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/lint:studio.android.sdktools.lint-api"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//prebuilts/studio/intellij-sdk:studio-sdk-plugin-Kotlin",
        "//tools/base/lint:studio.android.sdktools.lint-api[module]",
    ],
)

maven_library(
    name = "tools.lint-checks",
    srcs = glob([
        "libs/lint-checks/src/main/java/**/*.kt",
        "libs/lint-checks/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-checks:" + BASE_VERSION,
    lint_baseline = "libs/lint-checks/lint_baseline.xml",
    module_name = "lint-checks",
    notice = "libs/lint-checks/src/main/resources/NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-checks/src/main/resources",
    resources = glob(
        include = ["libs/lint-checks/src/main/resources/**"],
        exclude = ["libs/lint-checks/src/main/resources/NOTICE"],
    ),
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
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-analysis",
        "@maven//:org.ow2.asm.asm-tree",
    ],
)

maven_library(
    name = "tools.lint-gradle",
    # TODO: move resources out of java?
    srcs = glob([
        "libs/lint-gradle/src/main/java/**/*.kt",
        "libs/lint-gradle/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools.lint:lint-gradle:" + BASE_VERSION,
    lint_baseline = "libs/lint-gradle/lint_baseline.xml",
    module_name = "lint-gradle",
    notice = "libs/lint-gradle/NOTICE",
    resource_strip_prefix = "tools/base/lint/libs/lint-gradle",
    resources = glob(
        include = ["libs/lint-gradle/src/main/java/**"],
        exclude = [
            "libs/lint-gradle/src/main/java/**/*.java",
            "libs/lint-gradle/src/main/java/**/*.kt",
        ],
    ),
    visibility = ["//visibility:public"],
    deps = [
        # NOTE NOTE NOTE - before changing this, note that lint dependencies
        # must also be reflected in [ReflectiveLintRunner#computeUrls] as well
        # so update both in sync
        ":tools.lint-api",
        ":tools.lint-checks",
        ":tools.lint-model",
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/lint/cli",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:org.codehaus.groovy.groovy-all",
        "@maven//:org.jetbrains.annotations",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
    ],
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
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.codehaus.groovy.groovy-all",
        "@maven//:org.jetbrains.annotations",
        "@maven//:org.jetbrains.intellij.deps.trove4j",
        "@maven//:org.jetbrains.kotlin.kotlin-reflect",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib",
        "@maven//:org.mockito.mockito-all",
        "@maven//:org.ow2.asm.asm-tree",
    ],
)

kotlin_library(
    name = "studio-checks",
    srcs = glob([
        "studio-checks/src/main/java/**/*.kt",
        "studio-checks/src/main/java/**/*.java",
    ]),
    lint_baseline = "studio-checks/lint_baseline.xml",
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
        "@maven//:junit.junit",
    ],
)

exports_files(
    srcs = ["studio-checks/empty_baseline.xml"],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "libs/intellij-core/src/main/java/**/*.java",
        "libs/lint-model/src/main/**/*.java",
        "libs/lint-model/src/main/**/*.kt",
        "libs/lint-model/src/main/resources/**",
        "libs/lint-api/src/main/**/*.java",
        "libs/lint-api/src/main/**/*.kt",
        "libs/lint-api/src/main/resources/**",
        "libs/lint-gradle/src/main/**/*.java",
        "libs/lint-gradle/src/main/**/*.kt",
        "libs/lint-gradle/src/main/resources/**",
        "libs/lint-checks/src/main/**/*.java",
        "libs/lint-checks/src/main/**/*.kt",
        "libs/lint-checks/src/main/resources/**",
        "libs/uast/src/main/java/**/*.java",
    ]) + [
        "libs/lint-model/build.gradle",
        "libs/lint-model/NOTICE",
        "libs/lint-api/build.gradle",
        "libs/lint-api/NOTICE",
        "libs/lint-gradle/build.gradle",
        "libs/lint-gradle/NOTICE",
        "libs/lint-checks/build.gradle",
        "libs/intellij-core/build.gradle",
        "libs/intellij-core/NOTICE",
        "libs/kotlin-compiler/build.gradle",
        "libs/kotlin-compiler/NOTICE",
        "libs/uast/build.gradle",
        "libs/uast/NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
