load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/profiler:perf_test.bzl", "perf_test")

package(default_testonly = True)

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
            "//tools/base/profiler/app:perfa_okhttp",
            "//tools/base/profiler/native/perfa:libperfa.so",
            "//tools/base/profiler/tests/android-mock:android-mock-dex",
            "//tools/base/profiler/tests/app-launcher:app-launcher-dex",
        ],
    }),
    tags = ["no_foundry"],  # b/73948555
)

# TODO: Have transform pull in dependency jar files, so we do not need to load multiple
# jar dependencies at runtime.
java_binary(
    name = "profilers-transform-main",
    srcs = [
        "testSrcs/com/android/tools/profiler/transform/ProfilerTransformMain.java",
    ],
    main_class = "com.android.tools.profiler.transform.ProfilerTransformMain",
    resources = ["profiler.properties"],
    runtime_deps = ["//tools/base/profiler/transform"],
    deps = [
        "//tools/base/profiler/transform",
        "//tools/base/testutils:studio.testutils_testlib",
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
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//tools/base/common:studio.common",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/base/profiler/transform",
        "//tools/base/testutils:studio.testutils_testlib",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "BasicTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/BasicPerfTest.java",
        "testSrcs/com/android/tools/profiler/JvmtiAgentTest.java",
    ],
    tags = ["no_foundry"],  # b/73948555
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "HttpTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/network/HttpUrlTest.java",
        "testSrcs/com/android/tools/profiler/network/OkHttpTest.java",
    ],
    tags = ["no_foundry"],  # b/73948555
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "MemoryTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/memory/MemoryTest.java",
    ],
    tags = ["no_foundry"],  # b/73948555
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "JniTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/memory/JniTest.java",
    ],
    tags = ["no_foundry"],  # b/73948555
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/base/profiler/tests/test-app:libmemorynativetest.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "EventTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/EventProfilerTest.java",
    ],
    tags = ["no_foundry"],  # b/73948555
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "EnergyTest",
    srcs = [
        "testSrcs/com/android/tools/profiler/energy/AlarmTest.java",
        "testSrcs/com/android/tools/profiler/energy/JobTest.java",
        "testSrcs/com/android/tools/profiler/energy/WakeLockTest.java",
    ],
    tags = ["no_foundry"],  # b/73948555
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":tests",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)
