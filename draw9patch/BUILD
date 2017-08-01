load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.draw9patch",
    # do not sort: must match IML order
    srcs = [
        "src/main/java",
        "src/main/resources",
    ],
    iml_files = ["draw9patch.iml"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/.idea/libraries:JUnit4[test]"],
)
