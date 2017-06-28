load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "libperfa.so",
    srcs = [
        "jvmti_helper.cc",
        "jvmti_helper.h",
        "memory/memory_tracking_env.cc",
        "memory/memory_tracking_env.h",
        "memory/stats.h",
        "perfa.cc",
        "scoped_local_ref.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-I$(GENDIR)/tools/base/profiler",
    ],
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/profiler/native/agent",
        "//tools/base/profiler/native/dex/slicer",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
    ],
)

android_cc_binary(
    name = "android",
    abis = [
        "x86",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":libperfa.so.stripped",
    filename = "libperfa.so",
    visibility = ["//visibility:public"],
)
