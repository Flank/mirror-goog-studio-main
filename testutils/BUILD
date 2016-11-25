load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.testutils",
    srcs = ["src/main/java"],
    tags = ["managed"],
    test_data = ["//prebuilts/studio/sdk:platforms/latest"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/common:studio.common"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:easymock-tools",
        "//tools/idea/.idea/libraries:mockito",
        "//tools/idea/.idea/libraries:truth",
        "//tools/idea/.idea/libraries:dexlib2",
    ],
)

java_library(
    name = "tools.testutils",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.easymock_easymock",
        "//tools/base/third_party:org.mockito_mockito-all",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)

java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    data = ["//prebuilts/studio/sdk:platforms/latest"],
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
    ],
)
