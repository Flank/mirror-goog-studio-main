package(default_visibility = ["//visibility:public"])

load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "backgroundtask_inspector_java_proto",
    srcs = ["resources/proto/backgroundtask_inspection.proto"],
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

app_inspection_jar(
    name = "agent",
    srcs = glob(["src/**/*.kt"]),
    out = "backgroundtask-inspection.jar",
    d8_flags = [
        "--min-api 26",  # Background task inspector is only supported on O+ devices.
    ],
    inspection_resource_strip_prefix = "tools/base/app-inspection/inspectors/backgroundtask/resources",
    inspection_resources = glob(["resources/META-INF/**"]),
    proto = ":backgroundtask_inspector_java_proto",
    # do not sort: fake_android must come before latest_jar in the classpath to override small pieces of it
    deps = [
        ":backgroundtask_inspector_java_proto",
        "//tools/base/app-inspection/inspectors/backgroundtask/fake-android:fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
        "//tools/base/bazel:studio-proto",
        "@maven//:androidx.annotation.annotation",
    ],
)

kotlin_test(
    name = "agent_tests",
    srcs = glob([
        "testSrc/**/*.kt",
        "testSrc/**/*.java",
    ]),
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests.jar",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//prebuilts/studio/sdk:platforms/latest_runtime_jar",
    ],
    deps = [
        ":agent-sources_undexed",
        "//tools/base/app-inspection/inspectors/backgroundtask/fake-android:fake_android",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)

studio_data(
    name = "bundle",
    files = ["backgroundtask-inspection.jar"],
    mappings = {"tools/base/app-inspection/inspectors/backgroundtask/": "app-inspection/"},
)
