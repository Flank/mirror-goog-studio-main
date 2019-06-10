load(
    "//tools/base/bazel:android.bzl",
    "select_android",
    "ANDROID_COPTS",
    "ANDROID_LINKOPTS",
    "android_cc_binary",
    "dex_library",
)

cc_library(
    name = "agent_lib",
    srcs = [
        "agent.cc",
        "agent.h",
        "async-stack/init.cc",
        "async-stack/init.h",
        "async-stack/inject_hooks.cc",
        "async-stack/inject_hooks.h",
        "log.h",
        "transform.h",
        "util.cc",
        "util.h",
    ] + select_android(
        ["log_android.cc"],
        ["log.cc"],
    ),
    copts = ANDROID_COPTS + [
        "-Wall",
        "-Wpedantic",
        "-Werror",
    ],
    linkopts = ANDROID_LINKOPTS,
    tags = [
        "no_windows",
    ],
    deps = [
        "//external:slicer",
        "//prebuilts/studio/jdk:jni_headers",
    ],
)

cc_binary(
    name = "libdebug.so",
    linkopts = ANDROID_LINKOPTS + select({
        # Undefined symbols cause confusing failures when ART tries to
        # attach the agent, so we enforce that all symbols are defined.
        "//tools/base/bazel:darwin": ["-Wl,-undefined,error"],
        "//conditions:default": [
            "-ldl",
            "-Wl,--no-undefined",
        ],
    }),
    linkshared = 1,
    tags = [
        "no_windows",
    ],
    deps = [
        ":agent_lib",
    ],
)

# Note: to run this stress test with sanitizers, add the following flags
# to your Bazel invocation:
#   --copt=-fsanitize=address,undefined
#   --linkopt=-fsanitize=address,undefined
cc_test(
    name = "async_stack_stress_test",
    srcs = [
        "test/async-stack/stress_test.cc",
    ],
    args = [
        package_name() + "/test/data/medium.dex",
    ],
    copts = [
        "-Wall",
        "-Wpedantic",
        "-Werror",
    ],
    data = [
        "test/data/medium.dex",
    ],
    linkstatic = 1,
    tags = [
        "no_windows",
    ],
    deps = [
        ":agent_lib",
        "//external:slicer",
    ],
)

android_cc_binary(
    name = "android",
    abis = [
        "x86",
        "x86_64",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = select({
        "//tools/base/bazel:windows": ":libdebug.so.stripped.exe",
        "//conditions:default": ":libdebug.so.stripped",
    }),
    filename = "libdebug.so",
    tags = [
        "no_windows",
    ],
    visibility = ["//visibility:public"],
)

java_library(
    name = "java",
    resource_jars = [
        "//tools/idea/java/debugger/debugger-agent-storage:intellij.java.debugger.agent.storage.jar",
    ],
)

dex_library(
    name = "debug",
    jars = [":java"],
    visibility = ["//visibility:public"],
)
