load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_network",
    srcs = [
        "connection_count_sampler.cc",
        "connectivity_sampler.cc",
        "internal_network_service.cc",
        "io_network_type_provider.cc",
        "net_stats_file_reader.cc",
        "network_cache.cc",
        "network_collector.cc",
        "network_service.cc",
        "speed_sampler.cc",
    ],
    hdrs = [
        "connection_count_sampler.h",
        "connection_details.h",
        "connectivity_sampler.h",
        "fake_network_type_provider.h",
        "internal_network_service.h",
        "io_network_type_provider.h",
        "net_stats_file_reader.h",
        "network_cache.h",
        "network_collector.h",
        "network_constants.h",
        "network_profiler_component.h",
        "network_sampler.h",
        "network_service.h",
        "network_type_provider.h",
        "speed_sampler.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/profiler/native/perfd",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/profiler/native/perfd/statsd",
        "//tools/base/transport/daemon",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)

cc_test(
    name = "perfd_network_tests",
    srcs = [
        "connection_count_sampler_test.cc",
        "connectivity_sampler_test.cc",
        "net_stats_file_reader_test.cc",
        "network_collector_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-Itools/base/transport",
        "-Itools/base/transport/tests",
        "-I$(GENDIR)/tools/base/transport",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/network",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_network",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/tests/test:testutils",
    ],
)
