load("//tools/base/bazel:android.bzl", "dex_library")

dex_library(
    name = "apk",
    jars = [":apk_java_deploy.jar"],
    output = "out.apk",
    visibility = ["//tools/base/deploy/test:__pkg__"],
)

java_binary(
    name = "apk_java",
    srcs = glob(["com/android/tools/deploy/swapper/testapk/*.java"]),
    create_executable = 0,
)
