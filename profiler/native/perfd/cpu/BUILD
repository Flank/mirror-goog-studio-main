load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

cc_library(
    name = "profiler_cpu",
    srcs = [
        "atrace.cc",
        "atrace_manager.cc",
        "cpu_cache.cc",
        "cpu_collector.cc",
        "cpu_config.cc",
        "cpu_service.cc",
        "cpu_usage_sampler.cc",
        "internal_cpu_service.cc",
        "simpleperf.cc",
        "simpleperf_manager.cc",
        "thread_monitor.cc",
    ],
    hdrs = [
        "atrace.h",
        "atrace_manager.h",
        "cpu_cache.h",
        "cpu_collector.h",
        "cpu_config.h",
        "cpu_profiler_component.h",
        "cpu_service.h",
        "cpu_usage_sampler.h",
        "internal_cpu_service.h",
        "profiling_app.h",
        "simpleperf.h",
        "simpleperf_manager.h",
        "thread_monitor.h",
        "threads_sample.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)

cc_test(
    name = "perfd_cpu_tests",
    srcs = [
        "atrace_manager_test.cc",
        "cpu_config_test.cc",
        "cpu_service_test.cc",
        "cpu_usage_sampler_test.cc",
        "fake_atrace.h",
        "fake_simpleperf.h",
        "simpleperf_manager_test.cc",
        "simpleperf_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/cpu",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_cpu",
        "//external:gmock_main",
        "//tools/base/profiler/native/perfd:daemon",
        "//tools/base/profiler/native/test:testutils",
    ],
)
