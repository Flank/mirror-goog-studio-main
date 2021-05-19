load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "friends.impl",
    srcs = ["src"],
    iml_files = ["friends.impl.iml"],
    visibility = ["//visibility:public"],
    deps = ["//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"],
)

iml_module(
    name = "friends.tests",
    iml_files = ["friends.tests.iml"],
    test_friends = [":friends.impl"],
    test_srcs = ["testSrc"],
    test_tags = ["manual"],  # There are no tests here---just checking that things build.
    visibility = ["//visibility:public"],
    deps = [":friends.impl[module, test]"],
)
