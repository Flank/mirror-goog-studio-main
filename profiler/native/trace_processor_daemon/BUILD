package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:proto.bzl", "java_proto_library", "cc_grpc_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

cc_binary(
    name = "trace_processor_daemon",
    srcs = ["trace_processor_daemon.cc"],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
    ],
    # b/148211035
    tags = ["no_windows"],
    deps = [
        ":trace_processor_service_impl",
    ],
)

cc_library(
    name = "trace_processor_service_impl",
    srcs = ["trace_processor_service.cc"],
    hdrs = ["trace_processor_service.h"],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
    ],
    # b/148211035
    tags = ["no_windows"],
    deps = [
        ":cc_trace_processor_service_proto",
        "@perfetto//:trace_processor",
    ],
)

cc_test(
    name = "trace_processor_service_impl_test",
    size = "medium",
    srcs = ["trace_processor_service_test.cc"],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
    ],
    data = ["testdata/tank.trace"],
    linkstatic = 1,
    # b/148211035
    tags = ["no_windows"],
    deps = [
        ":trace_processor_service_impl",
        "@googletest//:gtest_main",
    ],
)

cc_grpc_proto_library(
    name = "cc_trace_processor_service_proto",
    srcs = ["trace_processor_service.proto"],
    grpc_support = 1,
)

# The following expose the TPD protos to Studio.
java_proto_library(
    name = "java_proto",
    srcs = ["trace_processor_service.proto"],
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.0.3",
)

java_jarjar(
    name = "traceprocessor_protos",
    srcs = [
        ":java_proto",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)
