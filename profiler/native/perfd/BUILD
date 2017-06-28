load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_library(
    name = "perfd_internal_headers",
    hdrs = [
        "daemon.h",
        "profiler_component.h",
        "profiler_service.h",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    visibility = ["//visibility:public"],
)

cc_binary(
    name = "perfd",
    srcs = [
        "agent_service.cc",
        "agent_service.h",
        "connector.cc",
        "connector.h",
        "daemon.cc",
        "daemon.h",
        "generic_component.cc",
        "generic_component.h",
        "perfd.cc",
        "profiler_component.h",
        "profiler_service.cc",
        "profiler_service.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    linkopts = ANDROID_LINKOPTS,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/perfd/cpu:profiler_cpu",
        "//tools/base/profiler/native/perfd/event:profiler_event",
        "//tools/base/profiler/native/perfd/graphics:profiler_graphics",
        "//tools/base/profiler/native/perfd/memory:profiler_memory",
        "//tools/base/profiler/native/perfd/network:profiler_network",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)

android_cc_binary(
    name = "android",
    abis = [
        "x86",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":perfd.stripped",
    filename = "perfd",
    visibility = ["//visibility:public"],
)
