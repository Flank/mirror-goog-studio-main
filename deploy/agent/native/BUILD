load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

# Used for build rule visibility.
package_group(
    name = "all",
    packages = ["//tools/base/deploy/agent/..."],
)

cc_binary(
    name = "libswap.so",
    srcs = [
        "android_wrapper.cc",
        "android_wrapper.h",
        "capabilities.h",
        "config.cc",
        "config.h",
        "hotswap.cc",
        "hotswap.h",
        "instant.cc",
        "instrumenter.cc",
        "instrumenter.h",
        "native_callbacks.cc",
        "native_callbacks.h",
    ],
    copts = ANDROID_COPTS + ["-I$(GENDIR)/tools/base/deploy/proto"] + [
        "-I$(GENDIR)/tools/base/deploy/agent/native",
    ],
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    tags = [
        "no_windows",
    ],
    visibility = ["//tools/base/deploy:__subpackages__"],
    deps = [
        ":instrumentation_header",
        "//external:slicer",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/deploy/agent/native/jni:agent_jni",
        "//tools/base/deploy/agent/native/utils:agent_utils",
        "//tools/base/deploy/proto:cc_proto",
    ],
)

cc_library(
    name = "instrumentation_header",
    hdrs = [
        ":instrumentation.jar.cc",
    ],
)

# Converts the jar containing instrumentation dex to an array in a CC source file.
genrule(
    name = "instrumentation_header_generator",
    srcs = [
        "//tools/base/deploy/agent/instrumentation",
    ],
    outs = [
        "instrumentation.jar.cc",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=cxx -variable=instrumentation_jar" +
          " -output=$(location instrumentation.jar.cc)" +
          " $(location //tools/base/deploy/agent/instrumentation:instrumentation)",
    tools = [
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:private"],
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
