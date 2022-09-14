load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

cc_library(
    name = "profiler_cpu",
    srcs = [
        "commands/start_cpu_trace.cc",
        "commands/stop_cpu_trace.cc",
        "commands/trace_command_utils.cc",
        "cpu_cache.cc",
        "cpu_collector.cc",
        "cpu_config.cc",
        "cpu_service.cc",
        "cpu_usage_sampler.cc",
        "internal_cpu_service.cc",
        "thread_monitor.cc",
        "thread_parser.cc",
    ],
    hdrs = [
        "commands/start_cpu_trace.h",
        "commands/stop_cpu_trace.h",
        "commands/trace_command_utils.h",
        "cpu_cache.h",
        "cpu_collector.h",
        "cpu_config.h",
        "cpu_profiler_component.h",
        "cpu_service.h",
        "cpu_usage_sampler.h",
        "internal_cpu_service.h",
        "thread_monitor.h",
        "thread_parser.h",
        "threads_sample.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/profiler/native/perfd/common",
        "-I$(GENDIR)/external/perfetto_repo",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/profiler/native/perfd/common:atrace",
        "//tools/base/profiler/native/perfd/common:perfetto",
        "//tools/base/profiler/native/perfd/common:simpleperf",
        "//tools/base/profiler/native/perfd/common:trace_manager",
        "//tools/base/profiler/native/perfd/common:trace_utils",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
        "@perfetto_repo//:cc_proto",
    ],
)

cc_test(
    name = "perfd_cpu_tests",
    srcs = [
        "commands/cpu_trace_commands_test.cc",
        "cpu_config_test.cc",
        "cpu_usage_sampler_test.cc",
        "thread_parser_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-Itools/base/profiler/native/tests",
        "-I$(GENDIR)/external/perfetto_repo",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/cpu",
    ],
    linkopts = [
        "-ldl",  # Needed for dladdr referenced in TermincationService
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_cpu",
        "//tools/base/profiler/native/perfd",
        "//tools/base/profiler/native/perfd/common:perfd_atrace_tests",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
        "@googletest//:gtest_main",
        "@perfetto_repo//:cc_proto",
    ],
)
