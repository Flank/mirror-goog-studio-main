load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

# Common helper used by both perfa_lib and transport agent.
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
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/utils",
    ],
)

# Profiler-specific code inside Transport JVMTI agent.
cc_library(
    name = "perfa_lib",
    srcs = [
        "jni_function_table.cc",
        "memory/memory_tracking_env.cc",
        "perfa.cc",
    ],
    hdrs = [
        "jni_function_table.h",
        "memory/memory_tracking_env.h",
        "memory/stats.h",
        "perfa.h",
    ] + glob(["transform/*.h"]),
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    tags = [
        "no_windows",
    ],
    deps = [
        ":jvmti_helper",
        "//external:slicer",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/profiler/native/agent",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)

# Transport pipeline JVMTI agent.
# TODO: move to transport/ directory.
cc_binary(
    name = "libperfa.so",
    srcs = [
        "transport_agent.cc",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
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
        ":perfa_lib",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/dynamic-layout-inspector:agent_command",
        "//tools/base/dynamic-layout-inspector:jni",
        "//tools/base/echo-sample:agent_command",
        "//tools/base/echo-sample:jni",
        "//tools/base/profiler/native/agent",
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
        "//tools/base/bazel:windows": ":libperfa.so",
        "//conditions:default": ":libperfa.so.stripped",
    }),
    filename = "libperfa.so",
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
)
