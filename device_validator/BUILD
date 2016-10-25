load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.dvlib",
    # do not sort: must match IML order
    srcs = ["dvlib/src/main/java"],
    resources = ["dvlib/src/main/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/common:studio.common"],
    deps = ["//tools/base/common:studio.common[module]"],
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
