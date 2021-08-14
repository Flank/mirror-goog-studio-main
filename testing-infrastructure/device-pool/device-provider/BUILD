load("//tools/base/bazel:kotlin.bzl", "maven_library")

maven_library(
    name = "device-provider",
    srcs = glob(["src/main/java/**/*.java"]),
    bundled_deps = [
        "//tools/base/build-system:gradle-api_neverlink",
    ],
    coordinates = "com.android.tools.internal.build.test:devicepool:0.1",
    resource_strip_prefix = "tools/base/testing-infrastructure/device-pool/device-provider/src/main/resources/",
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-core",
        "//tools/base/common:tools.common",
        "@maven//:com.google.guava.guava",
    ],
)
