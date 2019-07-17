load("//tools/base/bazel:proto.bzl", "cc_grpc_proto_library", "android_java_proto_library")

filegroup(
    name = "artifacts",
    srcs = [
        ":fake_device_proto",
        ":fake_runas",
        ":fake_shell",
    ],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
)

cc_grpc_proto_library(
    name = "proto",
    srcs = ["fake_device.proto"],
    grpc_support = 1,
    tags = ["no_windows"],
)

android_java_proto_library(
    name = "fake_device_proto",
    srcs = ["fake_device.proto"],
    grpc_support = 1,
    visibility = ["//visibility:public"],
)

cc_binary(
    name = "fake_shell",
    srcs = ["fake_shell.cc"],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":fake_device",
        ":proto",
        "//tools/base/deploy/common:log",
    ],
)

cc_binary(
    name = "fake_runas",
    srcs = ["fake_runas.cc"],
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":fake_device",
        ":proto",
        "//tools/base/deploy/common:log",
    ],
)

cc_library(
    name = "fake_device",
    srcs = ["fake_device.cc"],
    hdrs = ["fake_device.h"],
    tags = ["no_windows"],
    deps = [
        ":proto",
        "//tools/base/deploy/common:log",
    ],
)

cc_test(
    name = "integration_test",
    size = "small",
    srcs = ["tests.cc"],
    data = [
        "data/app/my.fake.app/sample.apk",
        "data/patchTest.txt",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//external:gmock_main",
        "//tools/base/deploy/installer:installer_lib",
        "//tools/base/profiler/native/test:testutils",
    ],
)
