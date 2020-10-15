load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

kotlin_library(
    name = "test-framework",
    testonly = True,
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/fakeandroid",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/transport/proto:transport_java_proto",
        "//tools/base/transport/test-framework",
    ],
)
