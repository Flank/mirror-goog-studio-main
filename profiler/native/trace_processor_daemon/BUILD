package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:utils.bzl", "java_jarjar")

cc_binary(
    name = "trace_processor_daemon",
    srcs = ["trace_processor_daemon.cc"],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
    ],
    deps = [
        ":trace_processor_service_impl",
        "@com_google_absl//absl/flags:flag",
        "@com_google_absl//absl/flags:parse",
        "@com_google_absl//absl/time",
    ],
)

cc_library(
    name = "trace_processor_service_impl",
    srcs = [
        "counters/counters_request_handler.cc",
        "memory/memory_request_handler.cc",
        "process_metadata/process_metadata_request_handler.cc",
        "scheduling/scheduling_request_handler.cc",
        "trace_events/android_frame_events_request_handler.cc",
        "trace_events/trace_events_request_handler.cc",
        "trace_metadata_request_handler.cc",
        "trace_processor_service.cc",
    ],
    hdrs = [
        "counters/counters_request_handler.h",
        "memory/memory_request_handler.h",
        "process_metadata/process_metadata_request_handler.h",
        "scheduling/scheduling_request_handler.h",
        "trace_events/android_frame_events_request_handler.h",
        "trace_events/trace_events_request_handler.h",
        "trace_metadata_request_handler.h",
        "trace_processor_service.h",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
    ],
    deps = [
        "//tools/base/profiler/native/trace_processor_daemon/proto:cc_trace_processor_service_proto",
        "@com_google_absl//absl/strings",
        "@perfetto//:libpprofbuilder",
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
        "testdata/garbage.trace",
        "testdata/tank.trace",
        "testdata/unity.heapprofd",
    ],
    linkstatic = 1,
    deps = [
        ":trace_processor_service_impl",
        "@com_google_absl//absl/strings",
        "@googletest//:gtest_main",
    ],
)

cc_test(
    name = "scheduling_request_handler_test",
    size = "medium",
    srcs = [
        "scheduling/scheduling_request_handler_test.cc",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
        "-Itools/base/profiler/native/trace_processor_daemon",
    ],
    data = [
        "testdata/tank.trace",
    ],
    linkstatic = 1,
    deps = [
        ":trace_processor_service_impl",
        "@googletest//:gtest_main",
    ],
)

cc_test(
    name = "trace_events_request_handler_test",
    size = "medium",
    srcs = [
        "trace_events/trace_events_request_handler_test.cc",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
        "-Itools/base/profiler/native/trace_processor_daemon",
    ],
    data = [
        "testdata/tank.trace",
    ],
    linkstatic = 1,
    deps = [
        ":trace_processor_service_impl",
        "@googletest//:gtest_main",
    ],
)

cc_test(
    name = "android_frame_events_request_handler_test",
    size = "medium",
    srcs = [
        "trace_events/android_frame_events_request_handler_test.cc",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
        "-Itools/base/profiler/native/trace_processor_daemon",
    ],
    data = [
        "testdata/frame.trace",
    ],
    linkstatic = 1,
    deps = [
        ":trace_processor_service_impl",
        "@googletest//:gtest_main",
    ],
)

cc_test(
    name = "counters_request_handler_test",
    size = "medium",
    srcs = [
        "counters/counters_request_handler_test.cc",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
        "-Itools/base/profiler/native/trace_processor_daemon",
    ],
    data = [
        "testdata/tank.trace",
    ],
    linkstatic = 1,
    deps = [
        ":trace_processor_service_impl",
        "@googletest//:gtest_main",
    ],
)

cc_test(
    name = "process_metadata_request_handler_test",
    size = "medium",
    srcs = [
        "process_metadata/process_metadata_request_handler_test.cc",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
        "-Itools/base/profiler/native/trace_processor_daemon",
    ],
    data = [
        "testdata/tank.trace",
    ],
    linkstatic = 1,
    deps = [
        ":trace_processor_service_impl",
        "@googletest//:gtest_main",
    ],
)

cc_test(
    name = "trace_metadata_request_handler_test",
    size = "medium",
    srcs = [
        "trace_metadata_request_handler_test.cc",
    ],
    copts = [
        "-I$(GENDIR)/tools/base/profiler/native/trace_processor_daemon",
        "-Itools/base/profiler/native/trace_processor_daemon",
    ],
    data = [
        "testdata/tank.trace",
    ],
    linkstatic = 1,
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
