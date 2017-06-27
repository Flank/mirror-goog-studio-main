sh_binary(
    name = "art-runner",
    srcs = ["//prebuilts/tools/linux-x86_64/art"],
    data = [
        "//tools/base/profiler/tests/android-mock:android-mock-dex",
        "//tools/base/profiler/tests/perfa-app:perfa-app-dex",
    ],
)

java_library(
    name = "tests",
    srcs = glob(["**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:studio.common",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)

java_test(
    name = "AllTest",
    size = "large",
    srcs = select({
        "//tools/base/bazel:darwin": ["testSrcs/com/android/tools/profiler/NoOpTest.java"],
        "//tools/base/bazel:windows": ["testSrcs/com/android/tools/profiler/NoOpTest.java"],
        "//conditions:default": glob(["testSrcs/**/*Test.java"]),
    }),
    jvm_flags = [
        "-Dtest.suite.jar=AllTest.jar",
        "-Dperfd.location=$(location //tools/base/profiler/native/perfd)",
        "-Dart.location=/prebuilts/tools/linux-x86_64/art/bin/art",
        "-Dagent.location=/tools/base/profiler/native/agent",
        "-Dperfa.dex.location=$(location //tools/base/profiler/tests/perfa-app:perfa-app-dex)",
        "-Dandroid-mock.dex.location=$(location //tools/base/profiler/tests/android-mock:android-mock-dex)",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":art-runner",
        "//tools/base/profiler/native/agent:libsupportjni.so",
        "//tools/base/profiler/native/perfd",
        "//tools/base/profiler/tests/android-mock:android-mock-dex",
        "//tools/base/profiler/tests/perfa-app:perfa-app-dex",
        "//tools/base/testutils:tools.testutils",
    ],
    deps = [
        ":tests",
        "//tools/base/common:studio.common",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/base/profiler/native/perfd",
        "//tools/base/profiler/tests/android-mock:android-mock-dex",
        "//tools/base/profiler/tests/perfa-app:perfa-app-dex",
        "//tools/base/third_party:junit_junit",
    ],
)
