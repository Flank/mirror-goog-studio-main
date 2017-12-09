load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.chunkio-processor",
    srcs = ["src"],
    iml_files = ["chunkio-processor.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/chunkio:studio.chunkio[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
