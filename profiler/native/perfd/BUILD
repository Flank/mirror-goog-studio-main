load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_library(
    name = "perfd_internal_headers",
    hdrs = [
        "commands/command.h",
        "daemon.h",
        "event_buffer.h",
        "profiler_component.h",
        "profiler_service.h",
        "sessions/session.h",
        "sessions/sessions_manager.h",
        "termination_service.h",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

cc_library(
    name = "daemon",
    srcs = [
        "agent_service.cc",
        "agent_service.h",
        "commands/begin_session.cc",
        "commands/begin_session.h",
        "commands/command.h",
        "commands/end_session.cc",
        "commands/end_session.h",
        "connector.cc",
        "connector.h",
        "daemon.cc",
        "daemon.h",
        "event_buffer.cc",
        "event_buffer.h",
        "generic_component.cc",
        "generic_component.h",
        "profiler_component.h",
        "profiler_service.cc",
        "profiler_service.h",
        "sessions/session.cc",
        "sessions/session.h",
        "sessions/sessions_manager.cc",
        "sessions/sessions_manager.h",
        "termination_service.cc",
        "termination_service.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/perfd/cpu:profiler_cpu",
        "//tools/base/profiler/native/perfd/energy:profiler_energy",
        "//tools/base/profiler/native/perfd/event:profiler_event",
        "//tools/base/profiler/native/perfd/graphics:profiler_graphics",
        "//tools/base/profiler/native/perfd/memory:profiler_memory",
        "//tools/base/profiler/native/perfd/network:profiler_network",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)

cc_binary(
    name = "perfd",
    srcs = ["perfd.cc"],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":daemon",
        "@gflags_repo//:gflags_nothreads",
    ],
)

cc_test(
    name = "perfd_test",
    srcs = [
        "profiler_service_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":daemon",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
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
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)
