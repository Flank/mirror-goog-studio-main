load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "select_android")

cc_library(
    name = "installer_lib",
    srcs = [
        "apk_archive.cc",
        "apk_retriever.cc",
        "command.cc",
        "command_cmd.cc",
        "dump.cc",
        "package_manager.cc",
        "shell_command.cc",
        "swap.cc",
        "trace.cc",
        "workspace.cc",
    ],
    hdrs = [
        "apk_archive.h",
        "apk_retriever.h",
        "command.h",
        "command_cmd.h",
        "dump.h",
        "package_manager.h",
        "shell_command.h",
        "swap.h",
        "trace.h",
        "workspace.h",
    ],
    copts = ANDROID_COPTS + ["-I$(GENDIR)/tools/base/deploy/proto"],
    includes = [
        ".",
    ],
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":agent_header",
        "//tools/base/deploy/proto:cc_proto",
    ],
)

cc_binary(
    name = "installer",
    srcs = [
        "main.cc",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":installer_lib",
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
        "//tools/base/bazel:windows": ":installer",
        "//conditions:default": ":installer.stripped",
    }),
    filename = "installer",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

cc_library(
    name = "agent_header",
    hdrs = [
        ":agent.so.cc",
    ],
    tags = ["no_windows"],
)

# Converts the jar containing agent.so cc_library to an array in a CC source file.
genrule(
    name = "agent_header_generator",
    srcs = [
        "//tools/base/deploy/agent/native:libswap.so",
    ],
    outs = [
        "agent.so.cc",
    ],
    cmd = "$(location //tools/base/bazel:bin2c)" +
          " $(location //tools/base/deploy/agent/native:libswap.so)" +
          " $(location agent.so.cc)" +
          " agent_so",
    tags = ["no_windows"],
    tools = [
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:private"],
)

cc_test(
    name = "installer_test",
    size = "small",
    srcs = [
        "tests/tests.cc",
    ],
    data = [
        "tests/data/app/my.fake.app/sample.apk",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":installer_lib",
        "//external:gmock_main",
        "//tools/base/profiler/native/test:testutils",
    ],
)

sh_test(
    name = "endtoend_P",
    size = "small",
    srcs = [
        "tests/endtoend_p.sh",
    ],
    data = [
        ":installer",
    ] + glob(["tests/**/*"]),
    tags = [
        "no_windows",
    ],
)

sh_test(
    name = "endtoend_O",
    size = "small",
    srcs = [
        "tests/endtoend_o.sh",
    ],
    data = [
        ":installer",
    ] + glob(["tests/**/*"]),
    tags = [
        "no_windows",
    ],
)

sh_test(
    name = "endtoend_P_with_splits",
    size = "small",
    srcs = [
        "tests/endtoend_p_with_splits.sh",
    ],
    data = [
        ":installer",
    ] + glob(["tests/**/*"]),
    tags = [
        "no_windows",
    ],
)
