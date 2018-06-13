load("//tools/base/bazel:android.bzl", "dex_library")

java_library(
    name = "fakeandroid",
    srcs = glob(["srcs/com/android/tools/fakeandroid/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)

java_library(
    name = "android-mock",
    srcs = glob([
        "srcs/android/**/*.java",
        "srcs/com/google/android/**/*.java",
    ]),
    neverlink = 1,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/idea/.idea/libraries:Guava",
    ],
)

dex_library(
    name = "android-mock-dex",
    jars = ["android-mock"],
    visibility = ["//visibility:public"],
)
