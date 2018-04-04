load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "studio.android.sdktools.chunkio-processor",
    srcs = ["src"],
    iml_files = ["android.sdktools.chunkio-processor.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/chunkio:studio.android.sdktools.chunkio[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)
