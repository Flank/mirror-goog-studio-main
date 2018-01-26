load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar", "archive")

java_proto_library(
    name = "proto",
    srcs = glob(["src/**/*.proto"]),
    visibility = ["//visibility:public"],
)

java_jarjar(
    name = "jarjar",
    srcs = [
        ":proto",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

java_library(
    name = "aia-manifest-proto-1.0-jarjar",
    visibility = ["//visibility:public"],
    exports = [":jarjar"],
    runtime_deps = [":jarjar"],
)
