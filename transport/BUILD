load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_binary(
    name = "transport_main",
    srcs = ["native/main/transport.cc"],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native",
        "-Itools/base/transport/native",
        "-Itools/base/echo-sample",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkopts = ANDROID_LINKOPTS + [
        "-ldl",  # Needed for dladdr referenced in TermincationService
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/echo-sample:echo",
        "//tools/base/profiler/native/perfd",
        "//tools/base/transport/native/daemon",
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
    binary = ":transport_main.stripped",
    filename = "transport",
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)
