cc_library(
    name = "profiler_energy",
    srcs = [
        "energy_cache.cc",
        "energy_collector.cc",
        "energy_service.cc",
        "energy_usage_sampler.cc",
        "internal_energy_service.cc",
    ],
    hdrs = [
        "energy_cache.h",
        "energy_collector.h",
        "energy_profiler_component.h",
        "energy_service.h",
        "energy_usage_sampler.h",
        "internal_energy_service.h",
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
    name = "perfd_energy_tests",
    srcs = [
        "energy_cache_test.cc",
        "energy_usage_sampler_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    deps = [
        ":profiler_energy",
        "//external:gmock_main",
    ],
)
