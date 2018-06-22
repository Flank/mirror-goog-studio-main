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

sh_binary(
    name = "art-runner",
    srcs = ["//prebuilts/tools/linux-x86_64/art"],
    data = select({
        "//tools/base/bazel:darwin": [],
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            ":android-mock-dex",
            ":app-launcher-dex",
            "//prebuilts/tools/linux-x86_64/art:art_deps",
            "//prebuilts/tools/linux-x86_64/art:dex2oat",
        ],
    }),
    visibility = ["//visibility:public"],
)

dex_library(
    name = "app-launcher-dex",
    jars = [":app-launcher.jar"],
    output = "app-launcher.dex",
    visibility = ["//visibility:public"],
)

java_binary(
    name = "app-launcher",
    srcs = ["srcs/com/android/tools/applauncher/FakeAndroid.java"],
    main_class = "com.android.tools.applauncher.FakeAndroid",
    deps = [
        ":android-mock",
    ],
)
