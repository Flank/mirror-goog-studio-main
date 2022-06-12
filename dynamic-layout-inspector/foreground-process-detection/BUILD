load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "cc_grpc_proto_library")
load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")

cc_library(
    name = "foreground_process_tracker",
    srcs = [
        "native/commands/start_tracking_foreground_process.cc",
        "native/commands/stop_tracking_foreground_process.cc",
        "native/foreground_process_tracker.cc",
        "native/run_dumpsys.cc",
    ],
    hdrs = [
        "native/commands/start_tracking_foreground_process.h",
        "native/commands/stop_tracking_foreground_process.h",
        "native/foreground_process_tracker.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/dynamic-layout-inspector/foreground-process-detection/native",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_library(
    name = "mock_foreground_process_tracker",
    srcs = [
        "native/commands/start_tracking_foreground_process.cc",
        "native/commands/stop_tracking_foreground_process.cc",
        "native/foreground_process_tracker.cc",
        "native/test-utils/mock_run_dumpsys.cc",
    ],
    hdrs = [
        "native/commands/start_tracking_foreground_process.h",
        "native/commands/stop_tracking_foreground_process.h",
        "native/foreground_process_tracker.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/dynamic-layout-inspector/foreground-process-detection/native",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/transport/native/daemon",
        "//tools/base/transport/proto:cc_proto",
    ],
)

cc_binary(
    name = "mock_transport_main",
    tags = ["no_windows"],
    deps = [
        "//tools/base/dynamic-layout-inspector/foreground-process-detection:mock_foreground_process_tracker",
        "//tools/base/transport:transport_main_lib",
    ],
)

transport_test(
    name = "ForegroundProcessTrackerTest",
    srcs = glob(["test/ForegroundProcessTrackerTest.java"]),
    app_dexes = ["//tools/base/transport/test-framework/test-app:test-app"],
    tags = ["no_windows"],
    transport_main = ":mock_transport_main",
    deps = [
        "@maven//:com.google.truth.truth",
    ],
)

cc_test(
    name = "foreground_process_tracker_test",
    srcs = [
        "native/foreground_process_tracker_test.cc",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":foreground_process_tracker",
        "@googletest//:gtest_main",
    ],
)
