load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "android_cc_binary")

cc_library(
    name = "detector",
    srcs = ["detector.cc"],
    hdrs = ["detector.h"],
    copts = ANDROID_COPTS + [
        "-Itools/base",
    ],
    tags = ["no_windows"],
    deps = [
        "//tools/base/transport/native/utils",
    ],
)

cc_binary(
    name = "reporter",
    srcs = ["main.cc"],
    copts = ANDROID_COPTS + [
        "-Itools/base",
    ],
    linkopts = ANDROID_LINKOPTS,
    tags = ["no_windows"],
    deps = [
        ":detector",
    ],
)

android_cc_binary(
    name = "android",
    abis = [
        "x86",
        "armeabi-v7a",
    ],
    # Cross compiling from windows doesn't produce a stripped binary.
    binary = select({
        "//tools/base/bazel:windows": ":reporter",
        "//conditions:default": ":reporter.stripped",
    }),
    filename = "profileable_reporter",
)

cc_test(
    name = "detector_test",
    srcs = [
        "detector_test.cc",
    ],
    copts = [
        "-Itools/base",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    deps = [
        ":detector",
        "//tools/base/transport/native/utils",
        "@googletest//:gtest_main",
    ],
)
