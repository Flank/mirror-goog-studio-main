load("//tools/base/profiler:perf_test.bzl", "perf_test")
load("//tools/base/bazel:android.bzl", "dex_library")

package(default_testonly = True)

java_library(
    name = "libhotswap",
    srcs = glob(["src/main/com/android/tools/deploy/swapper/*.java"]),
    deps = ["//tools/base/bazel:langtools"],
)

perf_test(
    name = "BasicTest",
    srcs = glob(["src/test/com/android/tools/deploy/swapper/*Test.java"]),
    tags = ["no_test_foundry"],  # b/73948555
    test_app = ":test-app",
    deps = [
        ":libhotswap",
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
