load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "transport_main",
    srcs = ["transport.cc"],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/transport",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/profiler/native/perfd",
        "//tools/base/transport/daemon",
        "//tools/base/transport/proto:cc_proto",
        "@gflags_repo//:gflags_nothreads",
    ],
)

android_cc_binary(
    name = "android",
    abis = [
        "x86",
        "armeabi-v7a",
        "arm64-v8a",
    ],
    binary = ":transport_main",
    filename = "transport",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)
