load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

kotlin_library(
    name = "aaptcompiler",
    srcs = ["src/main/java"],
    pom = ":pom",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/base/build-system/aapt2-proto",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib-jdk8",
    ],
)

maven_pom(
    name = "pom",
    artifact = "aaptcompiler",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)
