package(default_visibility = ["//visibility:public"])

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
    srcs = [
        "memory/memory_request_handler.cc",
        "trace_processor_service.cc",
    ],
    hdrs = [
        "memory/memory_request_handler.h",
        "trace_processor_service.h",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
    ],
    # b/148211035
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/trace_processor_daemon/proto:cc_trace_processor_service_proto",
        "@perfetto//:trace_processor",
    ],
)

cc_test(
    name = "trace_processor_service_impl_test",
    size = "medium",
    srcs = [
        "memory/memory_request_handler_test.cc",
        "trace_processor_service_test.cc",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
        "-Itools/base/profiler/native/trace_processor_daemon",
    ],
    data = [
        "testdata/tank.trace",
        "testdata/unity.heapprofd",
    ],
    linkstatic = 1,
    # b/148211035
    tags = ["no_windows"],
    deps = [
        ":trace_processor_service_impl",
        "@googletest//:gtest_main",
    ],
)

java_jarjar(
    name = "traceprocessor_protos",
    srcs = [
        "//tools/base/profiler/native/trace_processor_daemon/proto:java_proto",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
)
