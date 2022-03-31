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
        "class_finder.cc",
        "class_finder.h",
        "crash_logger.cc",
        "crash_logger.h",
        "dex_verify.cc",
        "dex_verify.h",
        "hidden_api_silencer.cc",
        "hidden_api_silencer.h",
        "hotswap.cc",
        "hotswap.h",
        "instrumenter.cc",
        "instrumenter.h",
        "live_edit.cc",
        "live_edit.h",
        "live_edit_dex.cc",
        "live_edit_dex.h",
        "live_literal.cc",
        "live_literal.h",
        "native_callbacks.cc",
        "native_callbacks.h",
        "recompose.cc",
        "recompose.h",
        "swapper.cc",
        "swapper.h",
        "thread_suspend.cc",
        "thread_suspend.h",
        "transform/hook_transform.cc",
        "transform/hook_transform.h",
        "transform/modify_parameter_transform.cc",
        "transform/modify_parameter_transform.h",
        "transform/stub_transform.cc",
        "transform/stub_transform.h",
        "transform/transforms.cc",
        "transform/transforms.h",
        "variable_reinit.cc",
        "variable_reinit.h",
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
        ":jvmti_android",
        ":runtime_header",
        "//external:slicer",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/deploy/agent/native/jni:agent_jni",
        "//tools/base/deploy/agent/native/jni_dispatch",
        "//tools/base/deploy/common:common_lib",
        "//tools/base/deploy/common:log",
        "//tools/base/deploy/proto:cc_proto",
    ],
)

cc_library(
    name = "jvmti_android",
    srcs = [
        "jvmti/android.cc",
    ],
    hdrs = [
        "jvmti/android.h",
    ],
    visibility = ["//visibility:public"],
)

cc_library(
    name = "runtime_header",
    hdrs = [
        ":live_edit.dex.cc",
        ":runtime.jar.cc",
    ],
)

# Converts the jar containing runtime dex to an array in a CC source file.
genrule(
    name = "runtime_header_generator",
    srcs = [
        "//tools/base/deploy/agent/runtime",
    ],
    outs = [
        "runtime.jar.cc",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=cxx -variable=runtime_jar" +
          " -output=$(location runtime.jar.cc)" +
          " $(location //tools/base/deploy/agent/runtime:runtime)",
    tools = [
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:private"],
)

genrule(
    name = "live_edit_header_generator",
    srcs = [
        "//tools/base/deploy/agent/runtime:live_edit.dex",
    ],
    outs = [
        "live_edit.dex.cc",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=cxx -variable=live_edit_dex" +
          " -output=$(location live_edit.dex.cc)" +
          " $(location //tools/base/deploy/agent/runtime:live_edit.dex)",
    tools = [
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:private"],
)

android_cc_binary(
    name = "android-libswap",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = select({
        "//tools/base/bazel:windows": ":libswap.so.stripped.exe",
        "//conditions:default": ":libswap.so.stripped",
    }),
    filename = "android-libswap.so",
    tags = [
        "no_windows",
    ],
    visibility = ["//tools/base/deploy:__subpackages__"],
)
