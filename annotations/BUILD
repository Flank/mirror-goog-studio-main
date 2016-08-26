# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.android-annotations",
    srcs = ["src/main/java"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

# TODO: Keep only only one rule in this BUILD file, since there's no real need for the split here.
java_library(
    name = "annotations",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
)
