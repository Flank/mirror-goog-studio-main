load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS")
load("//tools/base/bazel:android.bzl", "jni_library")

cc_binary(
    name = "libsupportjni.so",
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":support",
    ],
)

cc_library(
    name = "support",
    srcs = [
        "support/cpu_trace.cc",
        "support/energy_passthrough.cc",
        "support/event_manager.cc",
        "support/event_manager.h",
        "support/event_passthrough.cc",
        "support/http_tracker.cc",
        "support/memory_stats_logger.cc",
        "support/memory_stats_logger.h",
        "support/profiler_service.cc",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native/agent",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/agent",
        "//tools/base/transport/agent:jni_wrappers",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)

jni_library(
    name = "supportjni",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": [":libsupportjni.so.stripped"],
    }),
)

cc_library(
    name = "memory_component",
    srcs = ["memory_component.cc"],
    hdrs = ["memory_component.h"],
    copts = ANDROID_COPTS + [
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = ["no_windows"],
    visibility = ["//tools/base/transport/agent:__pkg__"],
    deps = [
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)
