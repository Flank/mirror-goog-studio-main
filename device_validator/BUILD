load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.dvlib",
    srcs = [
        "dvlib/src/main/java",
        "dvlib/src/main/resources",
    ],
    deps = ["//tools/base/common:studio.common[module]"],
    exports = ["//tools/base/common:studio.common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_library(
    name = "tools.dvlib",
    srcs = glob(["dvlib/src/main/java/**"]),
    resources = glob(["dvlib/src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
    ],
)

