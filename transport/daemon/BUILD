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
        "commands/command.h",
        "connector.cc",
        "connector.h",
        "daemon.cc",
        "daemon.h",
        "event_buffer.cc",
        "event_buffer.h",
        "event_writer.h",
        "profiler_component.h",
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
    ],
    copts = [
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":daemon",
        "//external:gmock_main",
        "//tools/base/transport/proto:cc_proto",
    ],
)
