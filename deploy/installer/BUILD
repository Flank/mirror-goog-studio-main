load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "select_android")

# A few lines of explanation because what is happening in this BUILD file is not trivial.
# In order to keep the Deployer and the Installer in sync (a.k.a: Installer and Deployer were built
# at the same time and are compatible), we embed a "version" in both the Deployer jar and the
# installer android binaries. This version is the SHA-256 of the installer with a placeholder
# version number which we call "unversioned". The Installer linked with with a version is called
# "versioned".

# Installer lib is incomplete. It needs a version lib to link properly. Either provide:
# - :version_placeholder_lib
# - :version
# to have a fully resolved lib.
cc_library(
    name = "installer_lib",
    srcs = [
        "apk_archive.cc",
        "apk_retriever.cc",
        "command.cc",
        "command_cmd.cc",
        "dump.cc",
        "dump.h",
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
        ":agent_header",
        "//tools/base/deploy/common:common_lib",
        "//tools/base/deploy/proto:cc_proto",
    ],
)

# Provide a default version for installer_lib to link properly.
cc_library(
    name = "version_placeholder_lib",
    srcs = [
        "placeholder_version.cc",
    ],
)

# Provide a version for installer_lib based on the unversioned installer.
genrule(
    name = "version_generator",
    outs = [
        "version.cc",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=cxx -embed=false -variable=kVersion " +
          " -output=$(location version.cc)" +
          " $(locations //tools/base/deploy/installer:unversioned_installer)",
    tags = ["no_windows"],
    tools = [
        ":unversioned_installer",  # Used as hash function input. This is built for HOST arch.
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:private"],
)

# Installer linked with a placeholder. The sole purpose of this target is to be used as a tool in
# a genrule so it is hashed into a SHA-256 value in order to feed the versioned_installer.
cc_binary(
    name = "unversioned_installer",
    srcs = [
        "main.cc",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":installer_lib",
        ":version_placeholder_lib",
    ],
)

cc_binary(
    name = "versioned_installer",
    srcs = [
        "main.cc",
    ] + select({
        "//tools/base/bazel:host_windows": [":version_placeholder_lib"],
        "//conditions:default": [":version.cc"],
    }),
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
    binary = ":versioned_installer",
    filename = "installer",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

cc_library(
    name = "agent_header",
    hdrs = [
        ":agent.so.cc",
        ":agent_server.cc",
    ],
    tags = ["no_windows"],
)

# Converts the binary output of the agent.so cc_library to an array in a CC source file.
genrule(
    name = "agent_header_generator",
    srcs = [
        "//tools/base/deploy/agent/native:libswap.so",
    ],
    outs = [
        "agent.so.cc",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=cxx -variable=agent_so" +
          " -output=$(location agent.so.cc)" +
          " $(location //tools/base/deploy/agent/native:libswap.so)",
    tags = ["no_windows"],
    tools = [
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:private"],
)

# Converts the binary output of the agent_server cc binary to an array in a CC source file.
genrule(
    name = "agent_server_header_generator",
    srcs = [
        "//tools/base/deploy/agent/native:agent_server",
    ],
    outs = [
        "agent_server.cc",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=cxx -variable=agent_server" +
          " -output=$(location agent_server.cc)" +
          " $(location //tools/base/deploy/agent/native:agent_server)",
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
        ":version_placeholder_lib",
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
        ":versioned_installer",
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
        "versioned_installer",
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
        ":versioned_installer",
    ] + glob(["tests/**/*"]),
    tags = [
        "no_windows",
    ],
)
