load("//tools/base/bazel:android.bzl", "select_android", "ANDROID_COPTS", "ANDROID_LINKOPTS")

cc_library(
    name = "fakes_lib",
    srcs = [
        "fake_agent.cc",
    ],
    hdrs = [
        "fake_agent.h",
    ],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/deploy/common:common_lib",
        "//tools/base/deploy/common:log",
        "//tools/base/deploy/proto:cc_proto",
    ],
)
