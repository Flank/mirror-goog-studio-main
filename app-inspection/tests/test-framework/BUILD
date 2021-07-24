load("//tools/base/bazel:kotlin.bzl", "kotlin_library_legacy")

kotlin_library_legacy(
    name = "test-framework",
    testonly = True,
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/m2:androidx.annotation.annotation.1.1.0",
        "//prebuilts/tools/common/m2:com.google.truth.truth.0.42",
        "//prebuilts/tools/common/m2:junit.junit.4.12",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/fakeandroid",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/transport/proto:transport_java_proto",
        "//tools/base/transport/test-framework",
    ],
)
