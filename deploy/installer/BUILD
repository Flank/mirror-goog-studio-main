load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "select_android")
load("//tools/adt/idea/studio:studio.bzl", "studio_data")

# This is the one-and-only "version" rule (to rule them all).
# Everything within the installer uses the hash value output here to
# identify the current version on the device regardless of what layer
# it resides within the matryoshka chain. This will be the *ONLY*
# central place where we do any sort of hashing using Bin2C.
genrule(
    name = "version",
    srcs = [
        # This should be a list of all binaries that determine our current version.
        "//tools/base/deploy/agent/native:android-libswap/arm64-v8a/android-libswap.so",
        "//tools/base/deploy/installer:android-installer_raw/arm64-v8a/android-installer_raw",
        "//tools/base/deploy/installer:android-install-server/arm64-v8a/android-install-server",
        "//tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/arm64-v8a/coroutine_debugger_agent.so",
    ],
    outs = [
        "Version.java",
        "version.raw",
    ],
    cmd =  # Java
        "$(location //tools/base/bazel:bin2c) -lang=java -embed=false -variable=com.android.tools.deployer.Version -output=$(location Version.java) $(SRCS) && " +
        # Raw
        "$(location //tools/base/bazel:bin2c) -lang=raw -output=$(location version.raw) $(SRCS)",
    tags = ["no_windows"],
    tools = [
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:public"],
)

genrule(
    name = "android-installer",
    srcs = [
        ":android-installer_raw/x86/android-installer_raw",
        ":android-installer_raw/x86_64/android-installer_raw",
        ":android-installer_raw/armeabi-v7a/android-installer_raw",
        ":android-installer_raw/arm64-v8a/android-installer_raw",
        ":android-install-server/x86/android-install-server",
        ":android-install-server/x86_64/android-install-server",
        ":android-install-server/armeabi-v7a/android-install-server",
        ":android-install-server/arm64-v8a/android-install-server",
        "//tools/base/deploy/agent/native:android-libswap/x86/android-libswap.so",
        "//tools/base/deploy/agent/native:android-libswap/x86_64/android-libswap.so",
        "//tools/base/deploy/agent/native:android-libswap/armeabi-v7a/android-libswap.so",
        "//tools/base/deploy/agent/native:android-libswap/arm64-v8a/android-libswap.so",
        "//tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/x86/coroutine_debugger_agent.so",
        "//tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/x86_64/coroutine_debugger_agent.so",
        "//tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/armeabi-v7a/coroutine_debugger_agent.so",
        "//tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/arm64-v8a/coroutine_debugger_agent.so",
        ":version.raw",
    ],
    outs = [
        "android-installer/x86/installer",
        "android-installer/x86_64/installer",
        "android-installer/armeabi-v7a/installer",
        "android-installer/arm64-v8a/installer",
    ],
    cmd = """
       $(location //tools/base/bazel:matryoshka) \
           -i $(location :android-installer_raw/x86/android-installer_raw) \
           -o $(location android-installer/x86/installer) \
           -p coroutine_debugger_agent.so=$(location //tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/x86/coroutine_debugger_agent.so) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android-libswap/x86/android-libswap.so) \
           -p install_server=$(location //tools/base/deploy/installer:android-install-server/x86/android-install-server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :android-installer_raw/x86_64/android-installer_raw) \
           -o $(location android-installer/x86_64/installer) \
           -p coroutine_debugger_agent.so=$(location //tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/x86/coroutine_debugger_agent.so) \
           -p coroutine_debugger_agent64.so=$(location //tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/x86_64/coroutine_debugger_agent.so) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android-libswap/x86_64/android-libswap.so) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:android-libswap/x86/android-libswap.so) \
           -p install_server=$(location //tools/base/deploy/installer:android-install-server/x86_64/android-install-server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :android-installer_raw/armeabi-v7a/android-installer_raw) \
           -o $(location android-installer/armeabi-v7a/installer) \
           -p coroutine_debugger_agent.so=$(location //tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/armeabi-v7a/coroutine_debugger_agent.so) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android-libswap/armeabi-v7a/android-libswap.so) \
           -p install_server=$(location //tools/base/deploy/installer:android-install-server/armeabi-v7a/android-install-server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :android-installer_raw/arm64-v8a/android-installer_raw) \
           -o $(location android-installer/arm64-v8a/installer) \
           -p coroutine_debugger_agent.so=$(location //tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/armeabi-v7a/coroutine_debugger_agent.so) \
           -p coroutine_debugger_agent64.so=$(location //tools/base/debug/coroutine/native/agent:coroutine_debugger_agent/arm64-v8a/coroutine_debugger_agent.so) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android-libswap/arm64-v8a/android-libswap.so) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:android-libswap/armeabi-v7a/android-libswap.so) \
           -p install_server=$(location //tools/base/deploy/installer:android-install-server/arm64-v8a/android-install-server) \
           -p version=$(location :version.raw);
    """,
    tools = [
        "//tools/base/bazel:bin2c",
        "//tools/base/bazel:matryoshka",
    ],
    visibility = ["//visibility:public"],
)

genrule(
    name = "test-installer",
    srcs = [
        ":installer",
        ":install-server",
        "//tools/base/deploy/agent/native:libswap.so",
        ":version.raw",
        "//tools/base/debug/coroutine/native/agent:coroutine_debugger_agent.so",
    ],
    outs = [
        "android-installer/test-installer",
    ],
    cmd = """
       $(location //tools/base/bazel:matryoshka) \
           -i $(location :installer) \
           -o $(location android-installer/test-installer) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:libswap.so) \
           -p agent.so=$(location //tools/base/deploy/agent/native:libswap.so) \
           -p coroutine_debugger_agent.so=$(location //tools/base/debug/coroutine/native/agent:coroutine_debugger_agent.so) \
           -p install_server=$(location :install-server) \
           -p version=$(location :version.raw);
    """,
    tags = ["no_windows"],
    tools = [
        "//tools/base/bazel:bin2c",
        "//tools/base/bazel:matryoshka",
    ],
    visibility = ["//visibility:public"],
)

cc_library(
    name = "installer_lib",
    srcs = [
        "agent_interaction.cc",
        "apk_archive.cc",
        "base_install.cc",
        "base_swap.cc",
        "binary_extract.cc",
        "command.cc",
        "command_cmd.cc",
        "delta_install.cc",
        "delta_preinstall.cc",
        "dump.cc",
        "executor/executor.cc",
        "executor/executor_impl.cc",
        "executor/redirect_executor.cc",
        "highlander.cc",
        "install_coroutine_agent.cc",
        "live_literal_update.cc",
        "oid_push.cc",
        "overlay/overlay.cc",
        "overlay_install.cc",
        "overlay_swap.cc",
        "patch_applier.cc",
        "server/app_servers.cc",
        "server/install_client.cc",
        "swap.cc",
        "workspace.cc",
    ],
    hdrs = [
        "agent_interaction.h",
        "apk_archive.h",
        "base_install.h",
        "base_swap.h",
        "binary_extract.h",
        "command.h",
        "command_cmd.h",
        "delta_install.h",
        "delta_preinstall.h",
        "dump.h",
        "executor/executor.h",
        "executor/executor_impl.h",
        "executor/redirect_executor.h",
        "executor/runas_executor.h",
        "highlander.h",
        "install_coroutine_agent.h",
        "live_literal_update.h",
        "oid_push.h",
        "overlay/overlay.h",
        "overlay_install.h",
        "overlay_swap.h",
        "patch_applier.h",
        "server/app_servers.h",
        "server/install_client.h",
        "swap.h",
        "workspace.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/deploy/common",
        "-Itools/base/deploy/proto",
        "-I$(GENDIR)/tools/base/deploy/proto",
    ],
    includes = [
        ".",
    ],
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/bazel/native/matryoshka:libmatryoshka",
        "//tools/base/deploy/common:common_lib",
        "//tools/base/deploy/common:log",
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

cc_binary(
    name = "install-server",
    srcs = [
        "executor/executor.h",
        "executor/executor_impl.cc",
        "executor/executor_impl.h",
        "executor/redirect_executor.cc",
        "executor/redirect_executor.h",
        "executor/runas_executor.h",
        "overlay/overlay.cc",
        "overlay/overlay.h",
        "server/canary.h",
        "server/install_server.cc",
        "server/install_server.h",
        "server/main.cc",
        "server/parent_monitor.h",
    ] + select_android(
        [
            "server/parent_monitor_android.cc",
            "server/canary_android.cc",
        ],
        [
            "server/parent_monitor.cc",
            "server/canary.cc",
        ],
    ),
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/deploy/common:common_lib",
        "//tools/base/deploy/common:log",
        "//tools/base/deploy/proto:cc_proto",
    ],
)

android_cc_binary(
    name = "android-installer_raw",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = select({
        "//tools/base/bazel:windows": ":installer.stripped.exe",
        "//conditions:default": ":installer.stripped",
    }),
    filename = "android-installer_raw",
    tags = ["no_windows"],
    visibility = ["//tools/base/deploy:__subpackages__"],
)

android_cc_binary(
    name = "android-install-server",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = select({
        "//tools/base/bazel:windows": ":install-server.stripped.exe",
        "//conditions:default": ":install-server.stripped",
    }),
    filename = "android-install-server",
    tags = [
        "no_windows",
    ],
    visibility = ["//tools/base/deploy:__subpackages__"],
)

cc_test(
    name = "installer_tests",
    size = "small",
    srcs = [
        "executor/executor_test.cc",
        "overlay/overlay.cc",
        "overlay/overlay.h",
        "overlay/overlay_test.cc",
        "server/canary.cc",
        "server/canary.h",
        "server/install_server.cc",
        "server/install_server.h",
        "server/install_server_test.cc",
    ],
    args = [
        "$(location :executor_test_helper)",
    ],
    data = [
        ":executor_test_helper",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":installer_lib",
        "@googletest//:gtest_main",
    ],
)

cc_binary(
    name = "executor_test_helper",
    srcs = ["executor/executor_test_helper.cc"],
)

studio_data(
    name = "android-installer-bundle",
    files = [":android-installer"],
    mappings = {"tools/base/deploy/installer/android-installer/": "installer/"},
    visibility = ["//visibility:public"],
)
