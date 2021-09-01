package(default_visibility = ["//visibility:public"])

load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "network_inspector_java_proto",
    srcs = ["resources/proto/network-inspector.proto"],
    grpc_support = True,
    java_deps = ["@maven//:io.grpc.grpc-all"],
)

app_inspection_jar(
    name = "agent",
    srcs = glob([
        "src/**/*.kt",
        "src/**/*.java",
    ]),
    out = "network-inspector.jar",
    d8_flags = [
        "--min-api 26",  # Network inspector is only supported on O+ devices.
    ],
    inspection_resource_strip_prefix = "tools/base/app-inspection/inspectors/network/resources",
    inspection_resources = glob(["resources/META-INF/**"]),
    proto = ":network_inspector_java_proto",
    deps = [
        ":network_inspector_java_proto",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2:androidx.inspection.inspection.1.0.0",
        "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
        "//tools/base/bazel:studio-proto",
        "@maven//:androidx.annotation.annotation",
        "@maven//:com.squareup.okhttp.okhttp",
        "@maven//:com.squareup.okhttp3.okhttp",
        "@maven//:com.squareup.okio.okio",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
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
        "//tools/base/app-inspection/inspectors/network/fake-android:fake_android",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)

studio_data(
    name = "bundle",
    files = ["network-inspector.jar"],
    mappings = {"tools/base/app-inspection/inspectors/network/": "app-inspection/"},
)
