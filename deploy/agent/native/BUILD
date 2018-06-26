load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "libswap.so",
    srcs = [
        "capabilities.h",
        "hotswap.cc",
        "hotswap.h",
        "instant.cc",
        "jni_class.h",
        "jni_identifiers.h",
        "jni_object.h",
        "jni_util.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    tags = [
        "no_windows",
    ],
    visibility = ["//tools/base/deploy/swapper:__pkg__"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/deploy/agent/native/utils:agent_utils",
    ],
)

android_cc_binary(
    name = "android",
    abis = [
        "x86",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = select({
        "//tools/base/bazel:windows": ":libswap.so",
        "//conditions:default": ":libswap.so.stripped",
    }),
    filename = "libswap.so",
    tags = [
        "no_windows",
    ],
)
