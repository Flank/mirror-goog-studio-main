cc_binary(
    name = "libsupportjni.so",
    linkshared = 1,
    deps = [
        ":agent",
    ],
)

cc_library(
    name = "agent",
    srcs = [
        "agent.cc",
        "agent.h",
        "agent_component.h",
        "daemon_socket.cc",
        "daemon_socket.h",
        "memory_component.cc",
        "memory_component.h",
        "support/event_manager.cc",
        "support/event_manager.h",
        "support/event_passthrough.cc",
        "support/http_tracker.cc",
        "support/jni_wrappers.h",
        "support/memory_stats_logger.cc",
        "support/memory_stats_logger.h",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)
