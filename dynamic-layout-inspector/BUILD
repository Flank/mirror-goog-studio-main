load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "mac",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

cc_binary(
    name = "skia-grpc-server",
    srcs = glob([
        "src/main/native/skia/*.cc",
        "src/main/native/skia/*.h",
    ]),
    copts = [
        "-DSK_USER_CONFIG_HEADER=\\\"StudioConfig.h\\\"",
        "-UFORTIFY_SOURCE",
        "-DATRACE_TAG=ATRACE_TAG_VIEW",
        "-DSKIA_IMPLEMENTATION=1",
        "-DSK_PRINT_CODEC_MESSAGES",
        "-D_FORTIFY_SOURCE=1",
    ] + select({
        "windows": [],
        "//conditions:default": [
            "-std=c++14",
            "-Wno-implicit-fallthrough",
            "-Wno-missing-field-initializers",
            "-Wno-thread-safety-analysis",
            "-Wno-unused-parameter",
            "-Wno-unused-variable",
            "-fvisibility=hidden",
            "-fexceptions",
            "-mssse3",
        ],
    }),
    includes = ["src/main/native/skia"],
    linkopts = select({
        "windows": ["Ws2_32.lib"],
        "//conditions:default": ["-ldl"],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/dynamic-layout-inspector/proto:cc_proto",
        "@skia_repo//:libskia",
    ],
)

cc_library(
    name = "agent_command",
    srcs = [
        "src/main/native/agent/commands/layoutinspector_agent_command.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/dynamic-layout-inspector/src/main/native/agent",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_library(
    name = "jni",
    srcs = [
        "src/main/native/agent/component_tree_java_jni.cc",
        "src/main/native/agent/layoutinspector_java_jni.cc",
        "src/main/native/agent/properties_java_jni.cc",
        "src/main/native/agent/resource_configuration_java_jni.cc",
    ],
    copts = ANDROID_COPTS + [
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/agent",
        "//tools/base/transport/agent:jni_wrappers",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)
