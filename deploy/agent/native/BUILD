load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

# Used for build rule visibility.
package_group(
    name = "all",
    packages = ["//tools/base/deploy/agent/..."],
)

cc_binary(
    name = "libswap.so",
    srcs = [
        "agent.cc",
        "capabilities.h",
        "hotswap.cc",
        "hotswap.h",
        "instrumenter.cc",
        "instrumenter.h",
        "native_callbacks.cc",
        "native_callbacks.h",
        "swapper.cc",
        "swapper.h",
    ],
    copts = ANDROID_COPTS + [
        "-I$(GENDIR)/tools/base/deploy/proto",
        "-Itools/base/deploy/common",
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
        "//tools/base/deploy/common:common_lib",
        "//tools/base/deploy/common:log",
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
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":libswap.so",
    filename = "libswap.so",
    tags = [
        "no_windows",
    ],
    visibility = ["//tools/base/deploy:__subpackages__"],
)

cc_binary(
    name = "agent_server",
    srcs = [
        "agent_server.cc",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/deploy/common:common_lib",
    ],
)

cc_test(
    name = "agent_server_test",
    size = "small",
    srcs = ["agent_server_test.cc"],
    args = ["$(location :agent_server)"],
    data = [":agent_server"],
    linkstatic = 1,
    tags = [
        "no_mac",
        "no_windows",
    ],
    deps = [
        "//external:gmock_main",
        "//tools/base/deploy/test/fakes:fakes_lib",
    ],
)

android_cc_binary(
    name = "agent_server_android",
    abis = [
        "x86",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":agent_server",
    filename = "agent_server",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)
