load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

package(default_testonly = True)

java_library(
    name = "libhotswap",
    srcs = glob(["src/main/com/android/tools/deploy/swapper/*.java"]),
    deps = ["//tools/base/bazel:langtools"],
)

fake_android_test(
    name = "BasicTest",
    size = "medium",
    srcs = glob(["src/test/com/android/tools/deploy/swapper/*Test.java"]),
    data = [":test-app"],
    jvm_flags = ["-Dapp.dex.location=$(location :test-app)"],
    deps = [
        ":libhotswap",
        ":test-app",
        "//tools/base/bazel:langtools",
        "//tools/base/fakeandroid",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)

dex_library(
    name = "test-app",
    jars = [":test-app_java_deploy.jar"],
)

java_binary(
    name = "test-app_java",
    srcs = glob(["src/test/com/android/tools/deploy/swapper/testapp/**/*.java"]),
    create_executable = 0,
    deps = ["//tools/base/fakeandroid:android-mock"],
)
