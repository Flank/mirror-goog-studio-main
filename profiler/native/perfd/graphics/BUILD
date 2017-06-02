load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_graphics",
    srcs = [
        "graphics_cache.cc",
        "graphics_collector.cc",
        "graphics_framestats_sampler.cc",
        "graphics_service.cc",
    ],
    hdrs = [
        "graphics_cache.h",
        "graphics_collector.h",
        "graphics_framestats_sampler.h",
        "graphics_profiler_component.h",
        "graphics_service.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)

cc_test(
    name = "perfd_graphics_tests",
    srcs = [
        "graphics_framestats_sampler_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    linkstatic = 1,
    deps = [
        ":profiler_graphics",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
    ],
)
