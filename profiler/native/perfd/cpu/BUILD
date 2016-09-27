cc_library(
  name = "profiler_cpu",
  srcs = [
    "cpu_cache.cc",
    "cpu_collector.cc",
    "cpu_service.cc",
    "cpu_usage_sampler.cc",
    "simpleperf_manager.cc",
    "thread_monitor.cc",
  ],
  hdrs = [
    "cpu_cache.h",
    "cpu_collector.h",
    "cpu_profiler_component.h",
    "cpu_service.h",
    "cpu_usage_sampler.h",
    "simpleperf_manager.h",
    "thread_monitor.h",
  ],
  deps = [
    "//tools/base/profiler/native/perfd:perfd_internal_headers",
    "//tools/base/profiler/native/utils:profiler_utils",
    "//tools/base/profiler/proto:profiler_cc_proto",
  ],
  copts = [
    "-Itools/base/profiler/native",
    "-I$(GENDIR)/tools/base/profiler"
  ],
  visibility = ["//visibility:public"]
)