load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

# Profiler-specific code for the JVMTI agent.
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
        "-Itools/base/profiler/native/perfa",
        "-Itools/base/profiler/native/agent",
        "-Itools/base/profiler/native/agent/support",
    ],
    tags = [
        "no_windows",
    ],
    visibility = ["//tools/base/transport/native/agent:__pkg__"],
    deps = [
        "//external:slicer",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/profiler/native/agent:support",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/jvmti",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
)
