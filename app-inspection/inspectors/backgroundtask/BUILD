package(default_visibility = ["//visibility:public"])

load("//tools/adt/idea/studio:studio.bzl", "studio_data")
load("//tools/base/app-inspection:app_inspection.bzl", "app_inspection_jar")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library")

android_java_proto_library(
    name = "backgroundtask_inspector_java_proto",
    srcs = ["resources/proto/backgroundtask_inspection.proto"],
    grpc_support = 1,
    java_deps = ["//tools/base/third_party:io.grpc_grpc-all"],
    protoc_grpc_version = "1.21.1",
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
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/androidx/inspection/inspection/1.0.0:jar",
        "//tools/base/bazel:studio-proto",
    ],
)

studio_data(
    name = "bundle",
    files = ["backgroundtask-inspection.jar"],
    mappings = {"tools/base/app-inspection/inspectors/backgroundtask/": "app-inspection/"},
)
