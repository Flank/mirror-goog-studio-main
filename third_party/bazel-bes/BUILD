load("//tools/base/bazel:proto.bzl", "java_proto_library")

java_proto_library(
    name = "bes_proto",
    srcs = [
        "src/main/java/com/google/devtools/build/lib/buildeventstream/proto/build_event_stream.proto",
        "src/main/protobuf/command_line.proto",
        "src/main/protobuf/invocation_policy.proto",
        "src/main/protobuf/option_filters.proto",
    ],
    visibility = ["//visibility:public"],
)
