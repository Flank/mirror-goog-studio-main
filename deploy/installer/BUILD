load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "select_android")

cc_library(
    name = "ir2_installer_lib",
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
    binary = ":ir2_installer.stripped",
    filename = "ir2_installer",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

cc_test(
    name = "ir2_installer_test",
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
        ":ir2_installer_lib",
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
        ":ir2_installer",
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
        ":ir2_installer",
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
        ":ir2_installer",
    ] + glob(["tests/**/*"]),
    tags = [
        "no_windows",
    ],
)
