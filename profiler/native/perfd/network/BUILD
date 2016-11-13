package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_network",
    srcs = [
        "connection_sampler.cc",
        "connectivity_sampler.cc",
        "internal_network_service.cc",
        "net_stats_file_reader.cc",
        "network_cache.cc",
        "network_collector.cc",
        "network_service.cc",
        "speed_converter.cc",
        "speed_sampler.cc",
    ],
    hdrs = [
        "connection_details.h",
        "connection_sampler.h",
        "connectivity_sampler.h",
        "internal_network_service.h",
        "net_stats_file_reader.h",
        "network_cache.h",
        "network_collector.h",
        "network_constants.h",
        "network_profiler_component.h",
        "network_sampler.h",
        "network_service.h",
        "speed_converter.h",
        "speed_sampler.h",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    deps = [
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)

cc_test(
    name = "perfd_network_tests",
    srcs = [
        "connection_sampler_test.cc",
        "connectivity_sampler_test.cc",
        "net_stats_file_reader_test.cc",
        "speed_converter_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/network",
    ],
    deps = [
        ":profiler_network",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
    ],
)
