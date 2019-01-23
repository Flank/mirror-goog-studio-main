load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

# Shared library between components that want to implement / use the transport library.
# This library was initially part of the profilers perfd library that manged the services
# between studio and the device. Now the library manages a set of utils + base components
# to create the connection, buffer events, and handle commands on the pipeline.
# The implementation details of the profilers has been left in perfd.
# It is expected that new components will use this library to register command handlers
# via extending command.h and publish events via the event buffer on the daemon.
cc_library(
    name = "daemon",
    srcs = [
        "agent_service.cc",
        "agent_service.h",
        "commands/attach_agent.cc",
        "commands/attach_agent.h",
        "commands/command.h",
        "connector.cc",
        "connector.h",
        "daemon.cc",
        "daemon.h",
        "event_buffer.cc",
        "event_buffer.h",
        "event_writer.h",
        "service_component.h",
        "transport_component.cc",
        "transport_component.h",
        "transport_service.cc",
        "transport_service.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)

cc_test(
    name = "daemon_test",
    srcs = [
        "event_buffer_test.cc",
        "transport_service_test.cc",
    ],
    copts = [
        "-Itools/base/profiler/native",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":daemon",
        "//external:gmock_main",
        "//tools/base/profiler/native/perfd",
        "//tools/base/transport/proto:cc_proto",
    ],
)
