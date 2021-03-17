package(default_visibility = ["//visibility:public"])

load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "network_inspector_java_proto",
    srcs = ["resources/proto/network-inspector.proto"],
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.21.1",
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
    # do not sort: fake_android must come before latest_jar in the classpath to override small pieces of it
    deps = [
        ":network_inspector_java_proto",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0:jar",
        "//tools/base/bazel:studio-proto",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.4.1:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp/okhttp/2.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp3/okhttp/3.3.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okio/okio/1.6.0:jar",
        "//tools/base/app-inspection/inspectors/common:app-inspection.inspectors.common",
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
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)

studio_data(
    name = "bundle",
    files = ["network-inspector.jar"],
    mappings = {"tools/base/app-inspection/inspectors/network/": "app-inspection/"},
)
