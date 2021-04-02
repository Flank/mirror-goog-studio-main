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
    copts = ANDROID_COPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_library(
    name = "agent_command",
    srcs = [
        "commands/echo_agent_command.cc",
    ],
    hdrs = [
        "commands/echo_agent_command.h",
    ],
    copts = ANDROID_COPTS + [
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
        "-Itools/base/echo-sample",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/jvmti",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
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
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/agent:jni_wrappers",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)
