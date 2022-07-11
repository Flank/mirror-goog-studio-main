load("//tools/base/bazel:maven.bzl", "maven_repository")

maven_repository(
    name = "kotlin-m2repository",
    artifacts = [
        "@maven//:org.jetbrains.kotlin.jvm.org.jetbrains.kotlin.jvm.gradle.plugin",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
    visibility = ["//visibility:public"],
)
