load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_memory",
    srcs = [
        "internal_memory_service.cc",
        "memory_cache.cc",
        "memory_collector.cc",
        "memory_levels_sampler.cc",
        "memory_service.cc",
    ],
    hdrs = [
        "internal_memory_service.h",
        "memory_cache.h",
        "memory_collector.h",
        "memory_levels_sampler.h",
        "memory_profiler_component.h",
        "memory_service.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/transport/daemon",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)

cc_test(
    name = "perfd_memory_tests",
    srcs = [
        "memory_cache_test.cc",
        "memory_levels_sampler_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-Itools/base/transport",
        "-Itools/base/transport/tests",
        "-I$(GENDIR)/tools/base/transport",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/memory",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_memory",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/tests/test:testutils",
    ],
)
