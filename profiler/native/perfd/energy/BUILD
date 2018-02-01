load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_energy",
    srcs = [
        "energy_cache.cc",
        "internal_energy_service.cc",
    ],
    hdrs = [
        "energy_cache.h",
        "energy_profiler_component.h",
        "internal_energy_service.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)

cc_test(
    name = "perfd_energy_tests",
    srcs = ["energy_cache_test.cc"],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_energy",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
    ],
)
