load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "libperfa.so",
    srcs = [
        "jni_function_table.cc",
        "jni_function_table.h",
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
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//external:slicer",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/profiler/native/agent",
        "//tools/base/profiler/native/utils:profiler_utils",
        "//tools/base/profiler/proto:profiler_cc_proto",
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
