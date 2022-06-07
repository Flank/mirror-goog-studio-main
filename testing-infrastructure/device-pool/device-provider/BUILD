load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

kotlin_library(
    name = "device-provider",
    srcs = glob(["src/main/java/**/*.java"]),
    resource_strip_prefix = "tools/base/testing-infrastructure/device-pool/device-provider/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-core",
        "//tools/base/common:tools.common",
        "@maven//:com.google.guava.guava",
    ],
)
