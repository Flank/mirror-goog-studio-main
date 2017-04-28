load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.chunkio-processor",
    srcs = ["src"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/base/chunkio:studio.chunkio[module]"],
)
