load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "select_android")

cc_library(
    name = "common_lib",
    srcs = [
        "event.cc",
        "io.cc",
        "message_pipe_wrapper.cc",
        "socket.cc",
        "trace.cc",
        "utils.cc",
        "//tools/base/deploy/sites:sites.cc",
    ],
    hdrs = [
        "event.h",
        "io.h",
        "message_pipe_wrapper.h",
        "proto_pipe.h",
        "size_buffer.h",
        "socket.h",
        "trace.h",
        "utils.h",
        "//tools/base/deploy/sites:sites.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":log",
        "//tools/base/deploy/proto:cc_proto",
    ],
)

cc_library(
    name = "log",
    srcs = select_android(
        ["log.android.cc"],
        ["log.cc"],
    ),
    hdrs = [
        "log.h",
    ],
    copts = select_android(
        ["-std=c++11"],
        [],
    ) + select({
        "//tools/base/bazel:host_windows": [],
        "//conditions:default": ["-std=c++11"],
    }),
    tags = ["no_windows"],
    visibility = ["//tools/base/deploy:__subpackages__"],
    deps = [
        ":env",
    ],
)

cc_library(
    name = "env",
    srcs = select_android(
        ["env.android.cc"],
        ["env.cc"],
    ),
    hdrs = [
        "env.h",
    ],
    tags = ["no_windows"],
    visibility = ["//tools/base/deploy:__subpackages__"],
)

cc_test(
    name = "common_test",
    size = "medium",
    srcs = [
        "message_pipe_wrapper_test.cc",
        "sites_test.cc",
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
        "@googletest//:gtest_main",
    ],
)
