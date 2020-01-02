load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# A test framework for creating integration tests that communicate across a
# host / device connection using the Transport API.
kotlin_library(
    name = "test-framework",
    srcs = ["src/main/java"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/fakeandroid",
        "//tools/base/transport/proto:transport_java_proto",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)

transport_test(
    name = "SimpleTest",
    srcs = glob(["src/test/**/*.java"]),
    app_dexes = ["//tools/base/transport/test-framework/test-app:test-app"],
    deps = ["//tools/idea/.idea/libraries:truth"],
)
