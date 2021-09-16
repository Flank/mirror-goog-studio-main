load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

# Converts resource/DebugProbesKt.dex to an array in a CC source file.
# DebugProbesKt.dex is a dexed version of DebugProbesKt.bin, taken from the kotlinx-coroutines-core 1.4.x library jar.
# In order to update DebugProbesKt.dex, the new DebugProbesKt.bin should be taken from kotlinx-coroutines-core and dexed using D8, with minimum API level set to 26.
genrule(
    name = "DebugProbesKt_dex_generator",
    srcs = [
        "resource/DebugProbesKt.dex",
    ],
    outs = [
        "DebugProbesKt.cc",
        "DebugProbesKt.h",
    ],
    cmd = "$(location //tools/base/bazel:bin2c)" +
          " -lang=cxx -output=$(location DebugProbesKt.cc) -header=$(location DebugProbesKt.h) -variable=kDebugProbesKt" +
          " $(location resource/DebugProbesKt.dex)",
    tools = [
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:private"],
)

cc_binary(
    name = "coroutine_debugger_agent.so",
    srcs = [
        "agent.cc",
        "jni_utils.cc",
        "jni_utils.h",
        ":DebugProbesKt.cc",
        ":DebugProbesKt.h",
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
