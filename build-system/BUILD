load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load(
    "//tools/base/bazel:maven.bzl",
    "maven_java_import",
    "maven_pom",
)

iml_module(
    name = "studio.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
    iml_files = ["manifest-merger/manifest-merger.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    test_srcs = ["manifest-merger/src/test/java"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/sdklib:studio.sdklib"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)

filegroup(
    name = "gradle-distrib",
    srcs = ["//tools/external/gradle:gradle-distrib-4.1"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "gradle-api_neverlink",
    jars = ["//tools/external/gradle:gradle-api-4.1-jar"],
    neverlink = 1,
    visibility = ["//visibility:public"],
)

java_import(
    name = "gradle-api",
    jars = ["//tools/external/gradle:gradle-api-4.1-jar"],
    visibility = ["//visibility:public"],
)

kotlin_library(
    name = "tools.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    pom = ":manifest-merger.pom",
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
    srcs = ["manifest-merger/src/test/java"],
    jvm_flags = ["-Dtest.suite.jar=tools.manifest-merger_tests.jar"],
    resources = glob(
        include = ["manifest-merger/src/test/java/**"],
        exclude = [
            "manifest-merger/src/test/java/**/*.java",
            "manifest-merger/src/test/java/**/*.kt",
        ],
    ),
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
    jars = ["//tools/apksig:libapksig.jar"],
    pom = ":apksig.pom",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "apksig.pom",
    artifact = "apksig",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)
