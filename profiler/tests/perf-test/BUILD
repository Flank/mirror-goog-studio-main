load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/profiler:perf_test.bzl", "perf_test")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

package(
    default_testonly = True,
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
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
    ],
)

dex_library(
    name = "profiler-service",
    jars = ["//tools/base/profiler/app:studio-profiler"],
)

java_library(
    name = "test_lib",
    # This is a common library used by the integration tests, so we exclude the
    # the actual test files.
    srcs = glob(
        ["**/*.java"],
        exclude = ["**/*Test.java"],
    ),
    deps = [
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/fakeandroid",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/base/profiler/transform",
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/idea/.idea/libraries:truth",
    ],
)

coverage_java_test(
    name = "NativeBinarySizeTest",
    srcs = ["testSrcs/com/android/tools/profiler/performance/NativeBinarySizeTest.java"],
    data = [
        "//tools/base/transport:android/arm64-v8a/transport",
        "//tools/base/transport:android/armeabi-v7a/transport",
        "//tools/base/transport:android/x86/transport",
        "//tools/base/transport/agent:android/arm64-v8a/libjvmtiagent.so",
        "//tools/base/transport/agent:android/armeabi-v7a/libjvmtiagent.so",
        "//tools/base/transport/agent:android/x86/libjvmtiagent.so",
        "//tools/base/transport/agent:android/x86_64/libjvmtiagent.so",
    ],
    jvm_flags = ["-Dtest.suite.jar=NativeBinarySizeTest.jar"],
    tags = ["no_windows"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)

perf_test(
    name = "BasicTest",
    size = "small",
    srcs = [
        "testSrcs/com/android/tools/profiler/BasicPerfTest.java",
    ],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":test_lib",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "HttpTest",
    size = "large",
    srcs = [
        "testSrcs/com/android/tools/profiler/network/HttpUrlTest.java",
        "testSrcs/com/android/tools/profiler/network/OkHttpTest.java",
        "testSrcs/com/android/tools/profiler/network/UnifiedPipelineHttpUrlTest.java",
        "testSrcs/com/android/tools/profiler/network/UnifiedPipelineOkHttpTest.java",
    ],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":test_lib",
        "//tools/base/fakeandroid",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "MemoryTest",
    size = "medium",
    srcs = [
        "testSrcs/com/android/tools/profiler/memory/JniTest.java",
        "testSrcs/com/android/tools/profiler/memory/MemoryTest.java",
    ],
    tags = ["manual"],  # b/80501379
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":test_lib",
        "//tools/base/fakeandroid",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/base/profiler/tests/test-app:libmemorynativetest.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "EventTest",
    size = "large",
    srcs = [
        "testSrcs/com/android/tools/profiler/EventProfilerTest.java",
    ],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":test_lib",
        "//tools/base/fakeandroid",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
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
    deps = [
        ":test_lib",
        "//tools/base/fakeandroid",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

perf_test(
    name = "PerformanceTest",
    size = "large",
    srcs = [
        "testSrcs/com/android/tools/profiler/performance/LiveAllocationTest.java",
    ],
    tags = ["no_psq"],
    test_app = "//tools/base/profiler/tests/test-app:test-app",
    deps = [
        ":test_lib",
        "//tools/base/fakeandroid",
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/profiler/tests/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)
