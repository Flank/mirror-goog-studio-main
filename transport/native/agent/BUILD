load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

# Generic agent for managing communications between app and daemon
cc_library(
    name = "agent",
    srcs = [
        "agent.cc",
        "agent.h",
    ],
    hdrs = ["agent.h"],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native/agent",
    ],
    include_prefix = "agent",
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/agent:memory_component",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
)

# Common helper for JNI libraries.
cc_library(
    name = "jni_wrappers",
    srcs = ["jni_wrappers.h"],
    hdrs = ["jni_wrappers.h"],
    include_prefix = "agent",
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
    ],
)

# Transport pipeline JVMTI agent.
cc_binary(
    name = "libjvmtiagent.so",
    srcs = [
        "transport_agent.cc",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/profiler/native/agent",
        "-Itools/base/echo-sample",
        "-Itools/base/app-inspection/native/include",
    ],
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/app-inspection:agent_command",
        "//tools/base/app-inspection:jni",
        "//tools/base/echo-sample:agent_command",
        "//tools/base/echo-sample:jni",
        "//tools/base/profiler/native/perfa:perfa_lib",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/jvmti",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
)

android_cc_binary(
    name = "android",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    # Cross compiling from windows doesn't produce a stripped binary.
    binary = select({
        "//tools/base/bazel:windows": ":libjvmtiagent.so",
        "//conditions:default": ":libjvmtiagent.so.stripped",
    }),
    filename = "libjvmtiagent.so",
    visibility = ["//visibility:public"],
)
