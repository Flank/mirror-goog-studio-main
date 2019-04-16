load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary", "select_android")

# This is the one-and-only "version" rule (to rule them all).
# Everything within the installer uses the hash value output here to
# identify the current version on the device regardless of what layer
# it resides within the matryoshka chain. This will be the *ONLY*
# central place where we do any sort of hashing using Bin2C.
genrule(
    name = "version",
    srcs = [
        "//tools/base/deploy/agent/native:raw-agent_server/arm64-v8a/agent_server",
        "//tools/base/deploy/agent/native:raw-libswap/arm64-v8a/libswap.so",
        "//tools/base/deploy/installer:raw-installer/arm64-v8a/installer",
    ],
    outs = [
        "Version.java",
        "version.raw",
    ],
    cmd = "DEP=\"" +
          # This should be a list of all binaries that determine our current version.
          "$(location //tools/base/deploy/agent/native:raw-agent_server/arm64-v8a/agent_server) " +
          "$(location //tools/base/deploy/agent/native:raw-libswap/arm64-v8a/libswap.so) " +
          "$(location //tools/base/deploy/installer:raw-installer/arm64-v8a/installer) " +
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
        ":raw-installer/x86/installer",
        ":raw-installer/x86_64/installer",
        ":raw-installer/armeabi-v7a/installer",
        ":raw-installer/arm64-v8a/installer",
        "//tools/base/deploy/agent/native:raw-libswap/x86/libswap.so",
        "//tools/base/deploy/agent/native:raw-libswap/x86_64/libswap.so",
        "//tools/base/deploy/agent/native:raw-libswap/armeabi-v7a/libswap.so",
        "//tools/base/deploy/agent/native:raw-libswap/arm64-v8a/libswap.so",
        "//tools/base/deploy/agent/native:raw-agent_server/x86/agent_server",
        "//tools/base/deploy/agent/native:raw-agent_server/x86_64/agent_server",
        "//tools/base/deploy/agent/native:raw-agent_server/armeabi-v7a/agent_server",
        "//tools/base/deploy/agent/native:raw-agent_server/arm64-v8a/agent_server",
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
           -i $(location :raw-installer/x86/installer) \
           -o $(location android-installer/x86/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:raw-libswap/x86/libswap.so) \
           -p agent_server=$(location //tools/base/deploy/agent/native:raw-agent_server/x86/agent_server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :raw-installer/x86_64/installer) \
           -o $(location android-installer/x86_64/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:raw-libswap/x86_64/libswap.so) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:raw-libswap/x86/libswap.so) \
           -p agent_server=$(location //tools/base/deploy/agent/native:raw-agent_server/x86_64/agent_server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :raw-installer/armeabi-v7a/installer) \
           -o $(location android-installer/armeabi-v7a/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:raw-libswap/armeabi-v7a/libswap.so) \
           -p agent_server=$(location //tools/base/deploy/agent/native:raw-agent_server/armeabi-v7a/agent_server) \
           -p version=$(location :version.raw);

       $(location //tools/base/bazel:matryoshka) \
           -i $(location :raw-installer/arm64-v8a/installer) \
           -o $(location android-installer/arm64-v8a/installer) \
           -p agent.so=$(location //tools/base/deploy/agent/native:raw-libswap/arm64-v8a/libswap.so) \
           -p agent-alt.so=$(location //tools/base/deploy/agent/native:raw-libswap/armeabi-v7a/libswap.so) \
           -p agent_server=$(location //tools/base/deploy/agent/native:raw-agent_server/arm64-v8a/agent_server) \
           -p version=$(location :version.raw);
    """,
    tags = ["no_rbe_windows"],
    tools = [
        "//tools/base/bazel:bin2c",
        "//tools/base/bazel:matryoshka",
    ],
    visibility = ["//visibility:public"],
)

cc_binary(
    name = "bash_bridge",
    srcs = ["tests/bash_bridge.cc"],
    tags = ["no_windows"],
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
        "command.cc",
        "command_cmd.cc",
        "delta_install.cc",
        "delta_preinstall.cc",
        "dump.cc",
        "dump.h",
        "executor_impl.cc",
        "package_manager.cc",
        "patch_applier.cc",
        "redirect_executor.cc",
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

android_cc_binary(
    name = "raw-installer",
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
