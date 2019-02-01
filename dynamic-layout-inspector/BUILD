load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

cc_library(
    name = "agent_command",
    srcs = [
        "src/main/native/commands/layoutinspector_agent_command.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/dynamic-layout-inspector/src/main/native",
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
        "src/main/native/layoutinspector_java_jni.cc",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/profiler/native/agent",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)
