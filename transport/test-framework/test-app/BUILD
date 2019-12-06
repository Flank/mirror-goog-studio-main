load("//tools/base/bazel:android.bzl", "ANDROID_COPTS", "ANDROID_LINKOPTS", "dex_library")

package(default_visibility = ["//visibility:public"])

# Build a deploy jar. The contents of this jar will be dexed before being
# pushed onto a fake device.
#
# Note: Because android-mock is set to never link, compile dependencies use
# android-mock but the built jar does not include it.
#
# TODO: Refactor these rules so that external projects can define their own
# test apps which depend on this one. Move this to a .bzl file?
java_binary(
    name = "test-app_undexed",
    srcs = glob(["src/main/java/**/*.java"]),
    create_executable = 0,
    deps = ["//tools/base/fakeandroid:android-mock"],
)

# This target app should be specified as a target for the test_transport
# rule (in test-framework/transport_test.bzl)
dex_library(
    name = "test-app",
    jars = [":test-app_undexed_deploy.jar"],
    visibility = ["//visibility:public"],
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
