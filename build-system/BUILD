load("//tools/base/bazel:bazel.bzl", "iml_module")
load(
    "//tools/base/bazel:maven.bzl",
    "maven_java_import",
    "maven_java_library",
    "maven_pom",
)

iml_module(
    name = "studio.builder-test-api",
    srcs = ["builder-test-api/src/main/java"],
    tags = ["managed"],
    test_srcs = ["builder-test-api/src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = ["//tools/base/ddmlib:studio.ddmlib"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
)

iml_module(
    name = "studio.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
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
    ],
)

filegroup(
    name = "gradle-distrib",
    srcs = ["//tools/external/gradle:gradle-distrib-nightly"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "gradle-api_neverlink",
    neverlink = 1,
    visibility = ["//visibility:public"],
    exports = [":gradle-api"],
)

java_import(
    name = "gradle-api",
    jars = ["//tools/external/gradle:gradle-api-nightly"],
    visibility = ["//visibility:public"],
)

# TODO(bendowski): Move this to the right directory.
maven_java_library(
    name = "tools.builder-test-api",
    srcs = glob(["builder-test-api/src/main/java/**"]),
    pom = ":builder-test-api.pom",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

maven_pom(
    name = "builder-test-api.pom",
    artifact = "builder-test-api",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

java_test(
    name = "tools.builder-test-api_tests",
    srcs = glob(["builder-test-api/src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.builder-test-api_tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":tools.builder-test-api",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
)

maven_java_library(
    name = "tools.manifest-merger",
    srcs = glob(["manifest-merger/src/main/java/**"]),
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
    ],
)

maven_pom(
    name = "manifest-merger.pom",
    artifact = "manifest-merger",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

java_test(
    name = "tools.manifest-merger_tests",
    # TODO: Why are the xml files not under resources?
    srcs = glob(["manifest-merger/src/test/java/**/*.java"]),
    jvm_flags = ["-Dtest.suite.jar=tools.manifest-merger_tests.jar"],
    resources = glob(
        include = ["manifest-merger/src/test/java/**"],
        exclude = ["manifest-merger/src/test/java/**/*.java"],
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
        "//tools/base/third_party:junit_junit",
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
