load("//tools/base/bazel:maven.bzl", "maven_repo")

maven_repo(
    name = "kotlin-m2repository",
    artifacts = [
        "//tools/base/third_party:org.jetbrains.kotlin.jvm_org.jetbrains.kotlin.jvm.gradle.plugin",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-android-extensions-runtime",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib-jdk8",
    ],
    use_zip = False,
    visibility = ["//visibility:public"],
)
