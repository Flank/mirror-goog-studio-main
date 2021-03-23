load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")

cc_library(
    name = "agent_command",
    srcs = [
        "native/src/commands/app_inspection_agent_command.cc",
    ],
    hdrs = [
        "native/include/commands/app_inspection_agent_command.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/app-inspection/native/include/commands",
        "-Itools/base/app-inspection/native/include/common",
        "-Itools/base/app-inspection/native",
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":common",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/jvmti",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)

cc_library(
    name = "jni",
    srcs = [
        "native/src/app_inspection_java_jni.cc",
        "native/src/app_inspection_service.cc",
        "native/src/array_params_entry_hook.cc",
    ],
    hdrs = [
        "native/include/app_inspection_service.h",
        "native/include/app_inspection_transform.h",
        "native/include/array_params_entry_hook.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/app-inspection/native/include",
        "-Itools/base/app-inspection/native/include/common",
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":common",
        "//external:slicer",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/agent:jni_wrappers",
        "//tools/base/transport/native/jvmti",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)

cc_library(
    name = "common",
    srcs = glob(["native/src/common/**/*.cc"]),
    hdrs = glob([
        "native/include/common/**/*.h",
    ]),
    copts = ANDROID_COPTS + [
        "-Itools/base/app-inspection/native/include/common",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
    ],
    alwayslink = 1,
)
