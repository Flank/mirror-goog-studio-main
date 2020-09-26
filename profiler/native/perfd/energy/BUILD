load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_energy",
    srcs = [
        "energy_cache.cc",
        "energy_service.cc",
        "internal_energy_service.cc",
    ],
    hdrs = [
        "energy_cache.h",
        "energy_profiler_component.h",
        "energy_service.h",
        "internal_energy_service.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_test(
    name = "perfd_energy_tests",
    srcs = ["energy_cache_test.cc"],
    copts = [
        "-Itools/base/profiler/native",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":profiler_energy",
        "//tools/base/profiler/native/test:testutils",
        "//tools/base/transport/native/tests/test:testutils",
        "@googletest//:gtest_main",
    ],
)
