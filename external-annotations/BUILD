load("//tools/base/bazel:utils.bzl", "dir_archive")

# This directory contains "external annotations" in the format understood by
# IntelliJ and lint. They are passed in a zip archive to lint when it's invoked
# from Bazel and can also be added to IntelliJ library definitions to help static
# analysis.

dir_archive(
    name = "annotations",
    dir = "tools/base/external-annotations",
    files = glob(["**/annotations.xml"]),
    visibility = ["//visibility:public"],
)
