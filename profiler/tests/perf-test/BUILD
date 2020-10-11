load(":perf_test.bzl", "perf_test")
load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

package(
    default_testonly = True,
)

dex_library(
    name = "profiler-service",
    jars = ["//tools/base/profiler/app:studio-profiler"],
)

perf_test(
    name = "BasicTest",
    size = "small",
    srcs = ["testSrcs/com/android/tools/profiler/BasicTest.java"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = ["//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar"],
)

perf_test(
    name = "NetworkTest",
    size = "large",
    srcs = [
        "testSrcs/com/android/tools/profiler/network/HttpUrlTest.java",
        "testSrcs/com/android/tools/profiler/network/OkHttpTest.java",
        "testSrcs/com/android/tools/profiler/network/UnifiedPipelineHttpUrlTest.java",
        "testSrcs/com/android/tools/profiler/network/UnifiedPipelineOkHttpTest.java",
    ],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = ["//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar"],
)

perf_test(
    name = "MemoryTest",
    size = "medium",
    srcs = [
        "testSrcs/com/android/tools/profiler/memory/JniTest.java",
        "testSrcs/com/android/tools/profiler/memory/MemoryTest.java",
        "testSrcs/com/android/tools/profiler/memory/UnifiedPipelineJniTest.java",
        "testSrcs/com/android/tools/profiler/memory/UnifiedPipelineMemoryTest.java",
    ],
    app_runtime_deps = ["//tools/base/profiler/tests/test-app:libmemorynativetest.so"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = ["//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar"],
)

perf_test(
    name = "EventTest",
    size = "large",
    srcs = ["testSrcs/com/android/tools/profiler/event/EventProfilerTest.java"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = ["//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar"],
)

perf_test(
    name = "EnergyTest",
    size = "large",
    srcs = [
        "testSrcs/com/android/tools/profiler/energy/AlarmTest.java",
        "testSrcs/com/android/tools/profiler/energy/JobTest.java",
        "testSrcs/com/android/tools/profiler/energy/LocationTest.java",
        "testSrcs/com/android/tools/profiler/energy/WakeLockTest.java",
    ],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = ["//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar"],
)

perf_test(
    name = "PerformanceTest",
    size = "large",
    srcs = ["testSrcs/com/android/tools/profiler/performance/LiveAllocationTest.java"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar",
        "//tools/base/perf-logger:studio.perf-logger_testlib",
    ],
)

coverage_java_test(
    name = "NativeBinarySizeTest",
    srcs = ["testSrcs/com/android/tools/profiler/performance/NativeBinarySizeTest.java"],
    data = [
        "//tools/base/transport:android/arm64-v8a/transport",
        "//tools/base/transport:android/armeabi-v7a/transport",
        "//tools/base/transport:android/x86/transport",
        "//tools/base/transport/native/agent:android/arm64-v8a/libjvmtiagent.so",
        "//tools/base/transport/native/agent:android/armeabi-v7a/libjvmtiagent.so",
        "//tools/base/transport/native/agent:android/x86/libjvmtiagent.so",
        "//tools/base/transport/native/agent:android/x86_64/libjvmtiagent.so",
    ],
    jvm_flags = ["-Dtest.suite.jar=NativeBinarySizeTest.jar"],
    tags = ["no_windows"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
)
