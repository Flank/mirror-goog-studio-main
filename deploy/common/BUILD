load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS")

cc_library(
    name = "common_lib",
    srcs = [
        "message_pipe_wrapper.cc",
        "size_buffer.h",
        "socket.cc",
        "utils.cc",
    ],
    hdrs = [
        "message_pipe_wrapper.h",
        "socket.h",
        "utils.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/deploy/agent/native/utils:agent_utils",
        "//tools/base/deploy/proto:cc_proto",
    ],
)

cc_test(
    name = "common_test",
    size = "medium",
    srcs = [
        "message_pipe_wrapper_test.cc",
        "socket_test.cc",
    ],
    linkstatic = 1,
    tags = [
        "no_test_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":common_lib",
        "//external:gmock_main",
    ],
)
