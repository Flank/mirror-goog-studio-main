load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.intellij.android.flags",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.flags.iml"],
    tags = ["managed"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:Guava[test]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
