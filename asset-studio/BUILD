load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.assetstudio",
    srcs = ["src/main/java"],
    iml_files = ["assetstudio.iml"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/common:studio.common",
        "//tools/base/layoutlib-api:studio.layoutlib-api",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.common[module]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)
