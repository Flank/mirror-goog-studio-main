load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

cc_library(
    name = "echo",
    srcs = [
        "commands/echo_daemon_command.cc",
        "echo.cc",
    ],
    hdrs = [
        "commands/echo_daemon_command.h",
        "echo.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/transport/daemon",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_library(
    name = "agent_command",
    srcs = [
        "commands/echo_agent_command.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/echo-sample",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_library(
    name = "jni",
    srcs = [
        "echo_java_jni.cc",
    ],
    copts = ANDROID_COPTS + [
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
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
