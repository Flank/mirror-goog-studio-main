load("//tools/base/bazel:utils.bzl", "java_jarjar")

java_jarjar(
    name = "perfetto-protos",
    srcs = [
        "@perfetto_repo//:java_proto",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
