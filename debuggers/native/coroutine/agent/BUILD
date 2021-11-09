load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "coroutine_debugger_agent.so",
    srcs = [
        "agent.cc",
        "jni_utils.cc",
        "jni_utils.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//external:slicer",
        "//tools/base/transport/native/jvmti",
    ],
)

android_cc_binary(
    name = "coroutine_debugger_agent",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = select({
        "//tools/base/bazel:windows": ":coroutine_debugger_agent.so.stripped.exe",
        "//conditions:default": ":coroutine_debugger_agent.so.stripped",
    }),
    filename = "coroutine_debugger_agent.so",
    visibility = ["//visibility:public"],
)
