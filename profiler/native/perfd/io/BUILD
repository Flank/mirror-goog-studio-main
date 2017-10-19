load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_io",
    srcs = [
        "internal_io_service.cc",
        "io_app_cache.cc",
        "io_cache.cc",
        "io_service.cc",
        "io_speed_app_cache.cc",
        "io_speed_cache.cc",
    ],
    hdrs = [
        "internal_io_service.h",
        "io_app_cache.h",
        "io_cache.h",
        "io_profiler_component.h",
        "io_service.h",
        "io_session_details.h",
        "io_speed_app_cache.h",
        "io_speed_cache.h",
        "io_speed_details.h",
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
