load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:gradle.bzl", "gradle_build")
load(
    "//tools/base/bazel:maven.bzl",
    "maven_java_import",
    "maven_pom",
    "maven_repo",
)

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

kotlin_library(
    name = "tools.manifest-merger",
    srcs = glob([
        "manifest-merger/src/main/java/**/*.kt",
        "manifest-merger/src/main/java/**/*.java",
    ]),
    pom = ":manifest-merger.pom",
    resource_strip_prefix = "tools/base/build-system/manifest-merger",
    resources = ["manifest-merger/NOTICE"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
    ],
)

maven_pom(
    name = "manifest-merger.pom",
    artifact = "manifest-merger",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
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
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)

maven_java_import(
    name = "tools.apksig",
    jars = ["//tools/apksig:libapksig-all.jar"],
    pom = ":apksig.pom",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "apksig.pom",
    artifact = "apksig",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

# The Gradle APIs to build against and run against.
GRADLE_VERSION = "7.0"

filegroup(
    name = "gradle-distrib",
    srcs = ["//tools/external/gradle:gradle-distrib-" + GRADLE_VERSION],
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
    exports = ["//tools/base/third_party:org.gradle_gradle-tooling-api"],
    runtime_deps = ["//tools/base/third_party:org.slf4j_slf4j-api"],
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
    "//tools/base/build-system/aapt2",
    "//tools/base/build-system/gradle-core",
    "//tools/base/lint:tools.lint-gradle",
    "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/8.0.1:jar",
    "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/8.0.1:jar",
    "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-tree/8.0.1:jar",
    "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-analysis/8.0.1:jar",
    "//prebuilts/tools/common/m2/repository/com/google/errorprone/error_prone_annotations/2.3.2:jar",
    "//prebuilts/tools/common/m2/repository/org/checkerframework/checker-qual/2.5.2:jar",
    "//prebuilts/tools/common/m2/repository/com/github/javaparser/javaparser-core/3.17.0:jar",
]

maven_repo(
    name = "gradle_plugin_no_databinding_repo",
    artifacts = GRADLE_PLUGIN_NO_DATABINDING_ARTIFACTS,
    include_sources = True,
    use_zip = False,
    visibility = ["//visibility:public"],
)

# Full m2 repository to run the Gradle plugin.
# Only use if you need data-binding, otherwise use gradle_plugin_no_databinding_repo
GRADLE_PLUGIN_ARTIFACTS = GRADLE_PLUGIN_NO_DATABINDING_ARTIFACTS + [
    "//tools/data-binding:tools.compiler",
]

maven_repo(
    name = "gradle_plugin_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS,
    include_sources = True,
    use_zip = False,
    visibility = ["//visibility:public"],
)

# m2 repository used by performance tests
maven_repo(
    name = "performance_test_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS + [
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-gradle-plugin",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-android-extensions-runtime",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-compiler-embeddable",
        "//prebuilts/tools/common/m2/repository/androidx/activity/activity/1.3.0-alpha02:aar",
        "//prebuilts/tools/common/m2/repository/androidx/activity/activity-compose/1.3.0-alpha02:aar",
        "//prebuilts/tools/common/m2/repository/androidx/activity/activity-ktx/1.3.0-alpha02:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/animation/animation-core/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/animation/animation/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/compiler/compiler/1.0.0-beta07:jar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/foundation/foundation-layout/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/foundation/foundation/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/material/material-icons-core/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/material/material-icons-extended/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/material/material-ripple/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/material/material/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/runtime/runtime-livedata/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/runtime/runtime-saveable/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/runtime/runtime/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/ui/ui-geometry/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/ui/ui-graphics/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/ui/ui-text/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/ui/ui-tooling-data/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/ui/ui-tooling/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/ui/ui-unit/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/ui/ui-util/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/compose/ui/ui/1.0.0-beta07:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-common/2.3.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-livedata-core/2.3.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-runtime-ktx/2.3.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-runtime/2.3.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-viewmodel-ktx/2.3.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-viewmodel-savedstate/2.3.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/lifecycle/lifecycle-viewmodel/2.3.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/savedstate/savedstate-ktx/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/savedstate/savedstate/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/androidx/tracing/tracing/1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-android-extensions-runtime/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-build-common/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-daemon-client/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-scripting-common/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-scripting-jvm/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-android-extensions/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-compiler-runner/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-daemon-embeddable/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-gradle-plugin-api/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-gradle-plugin-model/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-native-utils/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-reflect/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-script-runtime/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-scripting-compiler-embeddable/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-scripting-compiler-impl-embeddable/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-util-io/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-annotation-processing-gradle/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-compiler-embeddable/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-compiler-embeddable/1.3.70-dev-withExperimentalGoogleExtensions-20200424:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-gradle-plugin/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlin/kotlin-util-klib/1.3.70:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.3.6:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core-common/1.3.6:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.3.6:jar",
    ],
    use_zip = False,
    visibility = ["//prebuilts/studio/buildbenchmarks:__subpackages__"],
)

# m2 repository packaged inside studio.
maven_repo(
    name = "studio_repo",
    artifacts = GRADLE_PLUGIN_ARTIFACTS,
    include_sources = True,
    use_zip = False,
    visibility = ["//visibility:public"],
)
