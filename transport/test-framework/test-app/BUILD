load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS")
load("//tools/base/transport/test-framework:transport_test.bzl", "transport_app")

transport_app(
    name = "test-app",
    srcs = glob(["src/main/java/**/*.java"]),
)

# Expose test-app as a library, so other projects can extend it with their
# own activities.
java_library(
    name = "libtest-app",
    srcs = glob(["src/main/java/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = ["//tools/base/fakeandroid:android-mock"],
)

# This native library is required as a dependency for all transport tests.
cc_binary(
    name = "libjni.so",
    srcs = ["src/main/cpp/jni.cpp"],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
    ],
)
