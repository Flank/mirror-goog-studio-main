load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "select_android")

# This is the one-and-only "version" rule (to rule them all).
# Everything within the installer uses the hash value output here to
# identify the current version on the device regardless of what layer
# it resides within the matryoshka chain. This will be the *ONLY*
# central place where we do any sort of hashing using Bin2C.
genrule(
    name = "version",
    srcs = [
        "//tools/base/deploy/agent/native:android-agent_server/arm64-v8a/android-agent_server",
        "//tools/base/deploy/agent/native:android-libswap/arm64-v8a/android-libswap.so",
        "//tools/base/deploy/installer:android-installer_raw/arm64-v8a/android-installer_raw",
        "//tools/base/deploy/installer:android-install-server/arm64-v8a/android-install-server",
    ],
    outs = [
        "Version.java",
        "version.raw",
    ],
    cmd = "DEP=\"" +
          # This should be a list of all binaries that determine our current version.
          "$(location //tools/base/deploy/agent/native:android-agent_server/arm64-v8a/android-agent_server) " +
          "$(location //tools/base/deploy/agent/native:android-libswap/arm64-v8a/android-libswap.so) " +
          "$(location //tools/base/deploy/installer:android-installer_raw/arm64-v8a/android-installer_raw) " +
          "$(location //tools/base/deploy/installer:android-install-server/arm64-v8a/android-install-server) " +
          "\";" +
          # Java
          "$(location //tools/base/bazel:bin2c) -lang=java -embed=false -variable=com.android.tools.deployer.Version -output=$(location Version.java) $$DEP && " +
          # Raw
          "$(location //tools/base/bazel:bin2c) -lang=raw -output=$(location version.raw) $$DEP",
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
        "//tools/base/deploy/agent/native:android-agent_server/x86/android-agent_server",
        "//tools/base/deploy/agent/native:android-agent_server/x86_64/android-agent_server",
        "//tools/base/deploy/agent/native:android-agent_server/armeabi-v7a/android-agent_server",
        "//tools/base/deploy/agent/native:android-agent_server/arm64-v8a/android-agent_server",
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
           -p agent.so=$(location //tools/base/deploy/agent/native:android-libswap/x86/android-libswap.so) \
           -p agent_server=$(location //tools/base/deploy/agent/native:android-agent_server/x86/android-agent_server) \
           -p install_server=$(location //tools/base/deploy/installer:android-install-server/x86/android-install-server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :android-installer_raw/x86_64/android-installer_raw) \
           -o $(location android-installer/x86_64/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android-libswap/x86_64/android-libswap.so) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:android-libswap/x86/android-libswap.so) \
           -p agent_server=$(location //tools/base/deploy/agent/native:android-agent_server/x86_64/android-agent_server) \
           -p install_server=$(location //tools/base/deploy/installer:android-install-server/x86_64/android-install-server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :android-installer_raw/armeabi-v7a/android-installer_raw) \
           -o $(location android-installer/armeabi-v7a/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android-libswap/armeabi-v7a/android-libswap.so) \
           -p agent_server=$(location //tools/base/deploy/agent/native:android-agent_server/armeabi-v7a/android-agent_server) \
           -p install_server=$(location //tools/base/deploy/installer:android-install-server/armeabi-v7a/android-install-server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :android-installer_raw/arm64-v8a/android-installer_raw) \
           -o $(location android-installer/arm64-v8a/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android-libswap/arm64-v8a/android-libswap.so) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:android-libswap/armeabi-v7a/android-libswap.so) \
           -p agent_server=$(location //tools/base/deploy/agent/native:android-agent_server/arm64-v8a/android-agent_server) \
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
        "//tools/base/deploy/agent/native:libswap.so",
        "//tools/base/deploy/agent/native:agent_server",
        ":version.raw",
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
           -p agent_server=$(location //tools/base/deploy/agent/native:agent_server) \
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
        "apk_archive.cc",
        "base_install.cc",
        "command.cc",
        "command_cmd.cc",
        "delta_install.cc",
        "delta_preinstall.cc",
        "dump.cc",
        "dump.h",
        "executor_impl.cc",
        "install_client.cc",
        "patch_applier.cc",
        "redirect_executor.cc",
        "swap.cc",
        "workspace.cc",
    ],
    hdrs = [
        "apk_archive.h",
        "base_install.h",
        "command.h",
        "command_cmd.h",
        "delta_install.h",
        "delta_preinstall.h",
        "dump.h",
        "executor.h",
        "executor_impl.h",
        "install_client.h",
        "patch_applier.h",
        "redirect_executor.h",
        "runas_executor.h",
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
        "executor.h",
        "executor_impl.cc",
        "executor_impl.h",
        "install_client.cc",
        "install_client.h",
        "install_server.cc",
        "install_server.h",
        "main_server.cc",
        "overlay.cc",
        "overlay.h",
        "redirect_executor.cc",
        "redirect_executor.h",
        "runas_executor.h",
        "workspace.cc",
        "workspace.h",
    ],
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
        "executor_test.cc",
        "install_server.cc",
        "install_server.h",
        "install_server_test.cc",
        "overlay.cc",
        "overlay.h",
        "overlay_test.cc",
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
        "//external:gmock_main",
    ],
)

cc_binary(
    name = "executor_test_helper",
    srcs = ["executor_test_helper.cc"],
)
