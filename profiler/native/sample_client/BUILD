load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "sample_client",
    srcs = ["sample_client.cc"],
    copts = ANDROID_COPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/transport/proto:cc_proto",
        "@gflags_repo//:gflags_nothreads",
    ],
)
