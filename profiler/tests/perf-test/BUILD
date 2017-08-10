load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/profiler:perf_test.bzl", "perf_test")

sh_binary(
    name = "art-runner",
    srcs = ["//prebuilts/tools/linux-x86_64/art"],
    data = select({
        "//tools/base/bazel:darwin": [],
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            ":profiler-service",
            "//prebuilts/tools/linux-x86_64/art:art_deps",
            "//prebuilts/tools/linux-x86_64/art:dex2oat",
            "//tools/base/profiler/app:perfa",
            "//tools/base/profiler/app:perfa_java",
            "//tools/base/profiler/native/perfa:libperfa.so",
            "//tools/base/profiler/tests/android-mock:android-mock-dex",
            "//tools/base/profiler/tests/app-launcher:app-launcher-dex",
        ],
    }),
)

# TODO: Have transform pull in dependency jar files, so we do not need to load multiple
# jar dependencies at runtime.
java_binary(
    name = "profilers-transform-main",
    srcs = [
        "testSrcs/com/android/tools/profiler/transform/ProfilerTransformMain.java",
    ],
    main_class = "com.android.tools.profiler.transform.ProfilerTransformMain",
    runtime_deps = ["//tools/base/profiler/transform"],
    deps = [
        "//tools/base/profiler/transform",
    ],
)

dex_library(
    name = "profiler-service",
    jars = ["//tools/base/profiler/app:studio-profiler"],
)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:studio.common",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/base/profiler/transform",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)

perf_test(
    name = "AllTest",
    srcs = glob(["testSrcs/**/BasicPerfTest.java"]),
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [":tests"],
)
