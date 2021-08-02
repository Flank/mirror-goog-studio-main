load("//tools/base/bazel:kotlin.bzl", "maven_library")

maven_library(
    name = "device-provider_bzl",
    srcs = glob(["src/main/java/**/*.java"]),
    bundled_deps = [
        "//tools/base/build-system:gradle-api_neverlink",
    ],
    coordinates = "com.android.tools.internal.build.test:devicepool:0.1",
    legacy_name = "device-provider",
    resource_strip_prefix = "tools/base/testing-infrastructure/device-pool/device-provider/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations:annotations_bzl",
        "//tools/base/build-system/builder:builder_bzl",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api_bzl",
        "//tools/base/build-system/gradle-core:gradle-core_bzl",
        "//tools/base/common:common_bzl",
        "@maven//:com.google.guava.guava",
    ],
)
