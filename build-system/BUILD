load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.builder-test-api",
    srcs = ["builder-test-api/src/main/java"],
    test_srcs = ["builder-test-api/src/test/java"],
    deps = [
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/idea/.idea/libraries:JUnit4[test]",
    ],
    exports = [
        "//tools/base/ddmlib:studio.ddmlib",
        "//tools/idea/.idea/libraries:guava-tools",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "studio.manifest-merger",
    srcs = ["manifest-merger/src/main/java"],
    test_srcs = ["manifest-merger/src/test/java"],
    deps = [
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
    exports = ["//tools/base/sdklib:studio.sdklib"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

# TODO(bendowski): Move this to the right directory.
java_library(
    name = "tools.builder-test-api",
    srcs = glob(["builder-test-api/src/main/java/**"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.guava_guava",
    ],
    visibility = ["//visibility:public"],
)

java_test(
    name = "tools.builder-test-api_tests",
    srcs = glob(["builder-test-api/src/test/java/**"]),
    deps = [
        ":tools.builder-test-api",
        "//tools/base/third_party:junit_junit",
    ],
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    test_class = "com.android.testutils.JarTestSuite",
    jvm_flags = ["-Dtest.suite.jar=tools.builder-test-api_tests.jar"],
)

java_library(
    name = "tools.manifest-merger",
    srcs = glob(["manifest-merger/src/main/java/**"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
    ],
    visibility = ["//visibility:public"],
)

java_test(
    name = "tools.manifest-merger_tests",
    # TODO: Why are the xml files not under resources?
    srcs = glob(["manifest-merger/src/test/java/**/*.java"]),
    resources = glob(["manifest-merger/src/test/java/**"], exclude = ["manifest-merger/src/test/java/**/*.java"]),
    deps = [
        ":tools.manifest-merger",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-all",
    ],
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    test_class = "com.android.testutils.JarTestSuite",
    jvm_flags = ["-Dtest.suite.jar=tools.manifest-merger_tests.jar"],
)
