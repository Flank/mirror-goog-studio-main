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
        "command.cc",
        "command_cmd.cc",
        "delta_install.cc",
        "delta_preinstall.cc",
        "dump.cc",
        "dump.h",
        "executor_impl.cc",
        "package_manager.cc",
        "patch_applier.cc",
        "swap.cc",
        "workspace.cc",
    ],
    hdrs = [
        "apk_archive.h",
        "command.h",
        "command_cmd.h",
        "delta_install.h",
        "delta_preinstall.h",
        "dump.h",
        "executor.h",
        "executor_impl.h",
        "package_manager.h",
        "patch_applier.h",
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
        ":agent_header",
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

android_cc_binary(
    name = "android-no-matryoshka",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":installer",
    filename = "installer",
    tags = ["no_windows"],
    visibility = ["//tools/base/deploy:__subpackages__"],
)

genrule(
    name = "android",
    srcs = [
        ":android-no-matryoshka/x86/installer",
        ":android-no-matryoshka/x86_64/installer",
        ":android-no-matryoshka/armeabi-v7a/installer",
        ":android-no-matryoshka/arm64-v8a/installer",
        "//tools/base/deploy/agent/native:android/x86/libswap.so",
        "//tools/base/deploy/agent/native:android/x86_64/libswap.so",
        "//tools/base/deploy/agent/native:android/armeabi-v7a/libswap.so",
        "//tools/base/deploy/agent/native:android/arm64-v8a/libswap.so",
    ],
    outs = [
        "android/x86/installer",
        "android/x86_64/installer",
        "android/armeabi-v7a/installer",
        "android/arm64-v8a/installer",
    ],
    cmd = """
        $(location //tools/base/bazel:bin2c) -lang=raw -output=version.raw \
            $(location //tools/base/deploy/agent/native:android/arm64-v8a/libswap.so) \
            $(location //tools/base/deploy/installer:android-no-matryoshka/arm64-v8a/installer);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location //tools/base/deploy/installer:android-no-matryoshka/x86/installer) \
           -o $(location android/x86/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android/x86/libswap.so) \
           -p version=version.raw;

       $(location //tools/base/bazel:matryoshka) \
           -i $(location //tools/base/deploy/installer:android-no-matryoshka/x86_64/installer) \
           -o $(location android/x86_64/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android/x86_64/libswap.so) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:android/x86/libswap.so) \
           -p version=version.raw;

       $(location //tools/base/bazel:matryoshka) \
           -i $(location //tools/base/deploy/installer:android-no-matryoshka/armeabi-v7a/installer) \
           -o $(location android/armeabi-v7a/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android/armeabi-v7a/libswap.so) \
           -p version=version.raw;

       $(location //tools/base/bazel:matryoshka) \
           -i $(location //tools/base/deploy/installer:android-no-matryoshka/arm64-v8a/installer) \
           -o $(location android/arm64-v8a/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:android/arm64-v8a/libswap.so) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:android/armeabi-v7a/libswap.so) \
           -p version=version.raw;
    """,
    tags = ["no_rbe_windows"],
    tools = [
        "//tools/base/bazel:bin2c",
        "//tools/base/bazel:matryoshka",
    ],
    visibility = ["//visibility:public"],
)

cc_library(
    name = "agent_header",
    srcs = [
        ":agent_server.cc",
    ],
    hdrs = [
        ":agent.so.h",
        ":agent_server.h",
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
        "agent.so.h",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=cxx -embed=false -variable=agent_so" +
          " -output=$(location agent.so.h)" +
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
        "agent_server.h",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=cxx -variable=agent_server" +
          " -output=$(location agent_server.cc)" +
          " -header=$(location agent_server.h)" +
          " $(location //tools/base/deploy/agent/native:agent_server)",
    tags = ["no_windows"],
    tools = [
        "//tools/base/bazel:bin2c",
    ],
    visibility = ["//visibility:private"],
)

cc_test(
    name = "integration_test",
    size = "small",
    srcs = [
        "tests/tests.cc",
    ],
    data = [
        "tests/data/app/my.fake.app/sample.apk",
        "tests/data/patchTest.txt",
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

cc_test(
    name = "installer_tests",
    size = "small",
    srcs = [
        "executor_test.cc",
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

sh_test(
    name = "endtoend_P",
    size = "small",
    srcs = [
        "tests/endtoend_p.sh",
    ],
    data = [
        ":versioned_installer",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protoc/3.0.0:exe",
        "//tools/base/deploy/proto:protobuffer_src",
    ] + glob(["tests/**/*"]),
    tags = [
        "manual",  # b/120995974
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
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protoc/3.0.0:exe",
        "//tools/base/deploy/proto:protobuffer_src",
    ] + glob(["tests/**/*"]),
    tags = [
        "manual",  # b/120995974
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
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protoc/3.0.0:exe",
        "//tools/base/deploy/proto:protobuffer_src",
    ] + glob(["tests/**/*"]),
    tags = [
        "manual",  # b/120995974
    ],
)
