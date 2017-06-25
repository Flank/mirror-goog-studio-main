load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

cc_library(
    name = "profiler_cpu",
    srcs = [
        "cpu_cache.cc",
        "cpu_collector.cc",
        "cpu_service.cc",
        "cpu_usage_sampler.cc",
        "procfs_files.cc",
        "simpleperf_manager.cc",
        "thread_monitor.cc",
    ],
    hdrs = [
        "cpu_cache.h",
        "cpu_collector.h",
        "cpu_profiler_component.h",
        "cpu_service.h",
        "cpu_usage_sampler.h",
        "procfs_files.h",
        "simpleperf_manager.h",
        "thread_monitor.h",
        "threads_sample.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
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
        "cpu_usage_sampler_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/cpu",
    ],
    deps = [
        ":profiler_cpu",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
    ],
)
