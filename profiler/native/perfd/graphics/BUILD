package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_graphics",
    srcs = [
        "graphics_collector.cc",
        "graphics_service.cc",
    ],
    hdrs = [
        "graphics_collector.h",
        "graphics_profiler_component.h",
        "graphics_service.h",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)
