load("//tools/base/bazel:android.bzl", "dex_library")
load(":perf_test.bzl", "perf_test")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

package(
    default_testonly = True,
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
        exclude = [
            "**/*Test.java",
            "testAppInspectorSrcs/**",
        ],
    ),
    deps = [
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-core/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/base/fakeandroid",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler/transform",
        "//tools/base/testutils:studio.android.sdktools.testutils_testlib",
        "//tools/base/transport/proto:transport_java_proto",
        "//tools/idea/.idea/libraries:JUnit4",
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
    test_app = "//tools/base/profiler/tests/legacy/test-app:test-app",
    deps = [
        ":test_lib",
        "//tools/base/fakeandroid",
        "//tools/base/profiler/tests/legacy/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)

# testAppInspectorSrcs is compiled into dex, that is injected by AppInspectionTest onto "device"
# This dex is similar to libraries inspectors code.
java_library(
    name = "test_inspector_java",
    srcs = glob([
        "testAppInspectorSrcs/**/*.java",
    ]),
    resource_strip_prefix = "tools/base/profiler/tests/legacy/perf-test/testAppInspectorSrcs/",
    resources = glob(["testAppInspectorSrcs/META-INF/**"]),
    deps = [
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0-SNAPSHOT:jar",
    ],
)

dex_library(
    name = "test_inspector_dex",
    jars = [":test_inspector_java"],
)

perf_test(
    name = "AppInspectionTest",
    size = "large",
    srcs = [
        "testSrcs/com/android/tools/profiler/app/inspection/AppInspectionTest.java",
    ],
    data = [
        ":test_inspector_dex",
    ],
    jvm_flags = [
        "-Dtest.inspector.dex.location=$(location :test_inspector_dex)",
    ],
    test_app = "//tools/base/profiler/tests/legacy/test-app:test-app",
    deps = [
        ":test_lib",
        "//tools/base/fakeandroid",
        "//tools/base/profiler/tests/legacy/test-app:libjni.so",
        "//tools/idea/.idea/libraries:truth",
    ],
)
