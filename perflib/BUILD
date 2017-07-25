load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.perflib",
    srcs = ["src/main/java"],
    tags = ["managed"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime[test]",
    ],
)
