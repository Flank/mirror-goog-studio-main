load("//tools/base/bazel:android.bzl", "select_android")

package(default_visibility = ["//tools/base/deploy/agent/native:all"])

cc_library(
    name = "agent_utils",
    srcs = select_android(
        ["log.android.cc"],
        ["log.cc"],
    ),
    hdrs = [
        "log.h",
    ],
    tags = ["no_windows"],
    deps = [],
)
