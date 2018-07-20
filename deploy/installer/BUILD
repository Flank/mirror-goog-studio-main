load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "select_android")

cc_library(
    name = "ir2_installer_lib",
    srcs = [
        "apk_archive.cc",
        "apk_archive.h",
        "apk_retriever.cc",
        "apk_retriever.h",
        "apk_retriever_android.cc",
        "apk_retriever_posix.cc",
        "command.cc",
        "command.h",
        "command_cmd.cc",
        "command_cmd.h",
        "dump.cc",
        "dump.h",
        "package_manager.cc",
        "package_manager.h",
        "shell_command.cc",
        "shell_command.h",
        "swap.cc",
        "swap.h",
        "trace.cc",
        "trace.h",
        "workspace.h",
    ],
    copts = ANDROID_COPTS + ["-I$(GENDIR)/tools/base/deploy/proto"],
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/deploy/proto:cc_proto",
    ],
)

cc_binary(
    name = "ir2_installer",
    srcs = [
        "main.cc",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":ir2_installer_lib",
    ],
)

android_cc_binary(
    name = "android",
    abis = [
        "x86",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":ir2_installer",
    filename = "ir2_installer",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

cc_test(
    name = "ir2_installer_test",
    size = "small",
    srcs = [
        "tests.cc",
    ],
    data = [
        "sample.apk",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":ir2_installer_lib",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
    ],
)
