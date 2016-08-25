load("//tools/base/bazel:bazel.bzl", "java_jarjar")

# Studio cannot handle proto3, so we jarjar our proto library together with grpc
# on a renamed namespace.
java_jarjar(
    name = "studio-profiler-grpc-1.0-jarjar",
    srcs = [
        "//tools/base/profiler/proto:libprofiler_proto.jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-all/0.13.2:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.0.0-beta-2:jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
