load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

# Generic agent for managing communications between app and daemon
cc_library(
    name = "agent",
    srcs = ["agent.cc"],
    hdrs = ["agent.h"],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native/agent",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/agent:memory_component",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)

# Common helper used by both perfa_lib and JVMTI agent.
cc_library(
    name = "jvmti_helper",
    srcs = ["jvmti_helper.cc"],
    hdrs = [
        "jvmti_helper.h",
        "scoped_local_ref.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/transport",
    ],
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/utils",
    ],
)

# Common helper for JNI libraries.
cc_library(
    name = "jni_wrappers",
    srcs = ["jni_wrappers.h"],
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
        "-Itools/base/transport",
        "-Itools/base/echo-sample",
        "-Itools/base/dynamic-layout-inspector/src/main/native",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":jvmti_helper",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/dynamic-layout-inspector:agent_command",
        "//tools/base/dynamic-layout-inspector:jni",
        "//tools/base/echo-sample:agent_command",
        "//tools/base/echo-sample:jni",
        "//tools/base/profiler/native/perfa:perfa_lib",
        "//tools/base/transport/agent",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
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
    binary = select({
        "//tools/base/bazel:windows": ":libjvmtiagent.so",
        "//conditions:default": ":libjvmtiagent.so.stripped",
    }),
    filename = "libjvmtiagent.so",
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
)
