load("//tools/base/bazel:android.bzl", "dex_library")

sh_binary(
    name = "art-runner",
    srcs = ["//prebuilts/tools/linux-x86_64/art"],
    data = select({
        "//tools/base/bazel:darwin": [],
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            ":profiler-service",
            ":transform-app",
            "//prebuilts/tools/linux-x86_64/art:art_deps",
            "//prebuilts/tools/linux-x86_64/art:dex2oat",
            "//tools/base/profiler/app:perfa",
            "//tools/base/profiler/app:perfa_java",
            "//tools/base/profiler/native/perfa:libperfa.so",
            "//tools/base/profiler/tests/android-mock:android-mock-dex",
            "//tools/base/profiler/tests/app-launcher:app-launcher-dex",
            "//tools/base/profiler/tests/test-app",
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

genrule(
    name = "transform-app_java",
    srcs = ["//tools/base/profiler/tests/test-app:test-app_java"],
    outs = ["transform-app_java.jar"],
    cmd = select({
        "//tools/base/bazel:darwin": "echo '' > ./$@",
        "//tools/base/bazel:windows": "echo '' > ./$@",
        "//conditions:default": "$(location :profilers-transform-main) ./$< ./$@",
    }),
    executable = 1,
    tools = select({
        "//tools/base/bazel:darwin": [],
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            ":profilers-transform-main",
        ],
    }),
)

dex_library(
    name = "profiler-service",
    jars = ["//tools/base/profiler/app:studio-profiler"],
)

dex_library(
    name = "transform-app",
    jars = select({
        "//tools/base/bazel:darwin": ["//tools/base/profiler/tests/test-app:test-app_java"],
        "//tools/base/bazel:windows": ["//tools/base/profiler/tests/test-app:test-app_java"],
        "//conditions:default": [":transform-app_java"],
    }),
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

# TODO: Move this to a bazel macro, so users do not need to define an instrumented
# and non-instrumented version of the test, they can just define a perf_test.
java_test(
    name = "AllTest-Instrumented",
    size = "small",
    srcs = select({
        "//tools/base/bazel:darwin": ["testSrcs/com/android/tools/profiler/NoOpTest.java"],
        "//tools/base/bazel:windows": ["testSrcs/com/android/tools/profiler/NoOpTest.java"],
        "//conditions:default": glob(["testSrcs/**/InstrumentedTest.java"]),
    }),
    jvm_flags = [
        "-Dtest.suite.jar=AllTest-Instrumented.jar",
        "-Dperfd.location=$(location //tools/base/profiler/native/perfd)",
        "-Dart.location=/prebuilts/tools/linux-x86_64/art/bin/art",
        "-Dagent.location=/tools/base/profiler/native/agent",
        "-Dperfa.dex.location=$(location //tools/base/profiler/tests/app-launcher:app-launcher-dex)",
        "-Dandroid-mock.dex.location=$(location //tools/base/profiler/tests/android-mock:android-mock-dex)",
        "-Dapp.dex.location=$(location :transform-app)",
        "-Dart.deps.location=prebuilts/tools/linux-x86_64/art/framework/",
        "-Dart.boot.location=prebuilts/tools/linux-x86_64/art/framework/x86_64/",
        "-Dprofiler.service.location=$(location :profiler-service)",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":art-runner",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler/native/agent:libsupportjni.so",
        "//tools/base/testutils:tools.testutils",
    ],
    deps = [
        ":profiler-service",
        ":tests",
        ":transform-app",
        "//tools/base/common:studio.common",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/base/profiler/native/perfd",
        "//tools/base/profiler/tests/android-mock:android-mock-dex",
        "//tools/base/profiler/tests/app-launcher:app-launcher-dex",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "AllTest-Jvmti",
    size = "small",
    srcs = select({
        "//tools/base/bazel:darwin": ["testSrcs/com/android/tools/profiler/NoOpTest.java"],
        "//tools/base/bazel:windows": ["testSrcs/com/android/tools/profiler/NoOpTest.java"],
        "//conditions:default": glob(["testSrcs/**/JvmtiTest.java"]),
    }),
    jvm_flags = [
        "-Dtest.suite.jar=AllTest-Jvmti.jar",
        "-Dperfd.location=$(location //tools/base/profiler/native/perfd)",
        "-Dart.location=/prebuilts/tools/linux-x86_64/art/bin/art",
        "-Dagent.location=/tools/base/profiler/native/agent",
        "-Dperfa.dir.location=/tools/base/profiler/native/perfa",
        "-Dperfa.location=$(location //tools/base/profiler/native/perfa:libperfa.so)",
        "-Dperfa.dex.location=$(location //tools/base/profiler/tests/app-launcher:app-launcher-dex)",
        "-Dandroid-mock.dex.location=$(location //tools/base/profiler/tests/android-mock:android-mock-dex)",
        "-Dart.deps.location=prebuilts/tools/linux-x86_64/art/framework/",
        "-Dart.boot.location=prebuilts/tools/linux-x86_64/art/framework/x86_64/",
        "-Dperfa.jar.location=$(location //tools/base/profiler/app:perfa)",
        "-Dapp.dex.location=$(location //tools/base/profiler/tests/test-app)",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":art-runner",
        "//tools/base/profiler:netty-grpc-jar",
        "//tools/base/profiler/app:perfa_java",
        "//tools/base/testutils:tools.testutils",
    ],
    deps = [
        ":tests",
        "//tools/base/common:studio.common",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/base/profiler/app:perfa",
        "//tools/base/profiler/native/perfa:libperfa.so",
        "//tools/base/profiler/native/perfd",
        "//tools/base/profiler/tests/android-mock:android-mock-dex",
        "//tools/base/profiler/tests/app-launcher:app-launcher-dex",
        "//tools/base/profiler/tests/test-app",
        "//tools/base/third_party:junit_junit",
    ],
)
