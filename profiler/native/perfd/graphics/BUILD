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
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_test(
    name = "perfd_graphics_tests",
    srcs = [
        "graphics_framestats_sampler_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
    ],
    data = [
        "//tools/base/profiler/native/testdata:perfd/graphics",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_graphics",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/native/tests/test:testutils",
        "@googletest//:gtest_main",
    ],
)
