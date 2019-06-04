load(
    "//tools/base/bazel:android.bzl",
    "select_android",
    "ANDROID_COPTS",
    "ANDROID_LINKOPTS",
    "android_cc_binary",
)

cc_binary(
    name = "libdebug.so",
    srcs = [
        "agent.cc",
        "agent.h",
        "log.h",
        "transform.h",
        "util.cc",
        "util.h",
    ] + select_android(
        ["log_android.cc"],
        ["log.cc"],
    ),
    copts = ANDROID_COPTS + [
        "-Wall",
        "-Wpedantic",
        "-Werror",
    ],
    linkopts = ANDROID_LINKOPTS + select({
        # Undefined symbols cause confusing failures when ART tries to
        # attach the agent, so we enforce that all symbols are defined.
        "//tools/base/bazel:darwin": ["-Wl,-undefined,error"],
        "//conditions:default": [
            "-ldl",
            "-Wl,--no-undefined",
        ],
    }),
    linkshared = 1,
    tags = [
        "no_windows",
    ],
    deps = [
        "//external:slicer",
        "//prebuilts/studio/jdk:jni_headers",
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
        "//tools/base/bazel:windows": ":libdebug.so.stripped.exe",
        "//conditions:default": ":libdebug.so.stripped",
    }),
    filename = "libdebug.so",
    tags = [
        "no_windows",
    ],
)
