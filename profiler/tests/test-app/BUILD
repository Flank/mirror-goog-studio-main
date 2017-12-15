load("//tools/base/bazel:android.bzl", "dex_library", "ANDROID_COPTS", "ANDROID_LINKOPTS")

package(default_visibility = ["//visibility:public"])

# Build a deploy jar that includes each library on which the binary depends. Unlike java_library,
# src and dependences are built into a single jar. Because android-mock is set never linked, compile
# dependency use android-mock but the built jar does not include it.
java_binary(
    name = "test-app_java",
    srcs = glob([
        "src/**/*.java",
    ]),
    create_executable = 0,
    deps = [
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp/okhttp/2.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp3/okhttp/3.3.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okio/okio/1.6.0:jar",
        "//tools/base/profiler/tests/android-mock",
    ],
)

dex_library(
    name = "test-app",
    jars = [":test-app_java_deploy.jar"],
)

cc_binary(
    name = "libnativetest.so",
    srcs = ["src/native/main.cpp"],
    copts = ANDROID_COPTS,
    linkopts = ANDROID_LINKOPTS,
    linkshared = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
    ],
)
