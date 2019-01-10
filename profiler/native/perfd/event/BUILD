load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

package(default_visibility = ["//visibility:public"])

cc_library(
    name = "profiler_event",
    srcs = [
        "event_cache.cc",
        "event_service.cc",
        "internal_event_service.cc",
    ],
    hdrs = [
        "event_cache.h",
        "event_profiler_component.h",
        "event_service.h",
        "internal_event_service.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/profiler/native/perfd",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/profiler/native/perfd:perfd_internal_headers",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)
