load("//tools/base/profiler:perf_test.bzl", "perf_test")

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
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":libhotswap",
        "//tools/base/bazel:langtools",
        "//tools/base/fakeandroid",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)
