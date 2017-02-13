# Keeping the tools/base fork of the dex classes is a temporary solution
# until we can import the sources directly.

java_library(
    name = "dex",
    srcs = glob(["java/**/*.java"]),
    visibility = ["//tools/dx/dalvik/dx:__pkg__"],
)

load("//tools/base/bazel:utils.bzl", "srcjar")

srcjar(
    name = "srcjar",
    java_library = ":dex",
    visibility = [
        "//tools/base/build-system/builder:__pkg__",
        "//tools/dx/dalvik/dx:__pkg__",
    ],
)
