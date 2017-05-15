load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.instantapp-deploy",
    srcs = ["src/main/java"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android-annotations[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/idea/.idea/libraries:Guava[library]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/third_party:org.mockito_mockito-core[test]",
        "//tools/base/instantapp-deploy/lib:aia-manifest-proto",
    ],
)
