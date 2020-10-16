load("//tools/base/transport/test-framework:transport_test.bzl", "transport_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# A test framework for creating integration tests that communicate across a
# host / device connection using the Transport API.
kotlin_library(
    name = "test-framework",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/fakeandroid",
        "//tools/base/transport/proto:transport_java_proto",
    ],
)

transport_test(
    name = "SimpleTest",
    srcs = glob(["src/test/**/*.java"]),
    app_dexes = ["//tools/base/transport/test-framework/test-app:test-app"],
    deps = ["//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar"],
)

cc_library(
    name = "native_test_support",
    srcs = glob(["src/main/native/**/*.cc"]),
    copts = [
        "-Itools/base/profiler/native/agent/",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)

cc_binary(
    name = "libagentrule-jni.dylib",
    linkshared = 1,
    tags = [
        "no_linux",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":native_test_support",
    ],
)

cc_binary(
    name = "libagentrule-jni.so",
    linkshared = 1,
    tags = [
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [
        ":native_test_support",
    ],
)
