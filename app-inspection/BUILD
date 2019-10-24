load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

cc_library(
    name = "agent_command",
    srcs = [
        "commands/app_inspection_agent_command.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/app_inspection",
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
        "app_inspection_java_jni.cc",
        "app_inspection_service.cc",
    ],
    hdrs = [
        "app_inspection_service.h",
    ],
    copts = ANDROID_COPTS + [
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ] + select({
        ":app_inspection_experimental": ["-DAPP_INSPECTION_EXPERIMENT=1"],
        "//conditions:default": [],
    }),
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/agent",
        "//tools/base/transport/agent:jni_wrappers",
        "//tools/base/transport/agent:jvmti_helper",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/utils",
    ],
)

# to compile app-inspection with experimental code run following command:
# bazel build //tools/adt/idea/android:artifacts --define app_inspection_experimental=true
config_setting(
    name = "app_inspection_experimental",
    values = {
        "define": "app_inspection_experimental=true",
    },
)
