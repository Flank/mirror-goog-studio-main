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
    name = "perfd_graphics_tests",
    srcs = [
        "graphics_framestats_sampler_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-Itools/base/transport/tests",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/graphics",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_graphics",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/tests/test:testutils",
    ],
)
