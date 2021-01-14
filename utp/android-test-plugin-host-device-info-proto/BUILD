load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

java_proto_library(
    name = "android-test-plugin-host-device-info-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    pom = ":pom",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "android-test-plugin-host-device-info-proto",
    group = "com.android.tools.utp",
    source = "//tools/buildSrc/base:base_version",
)

java_proto_library(
    name = "studio.android-test-plugin-host-device-info-proto",
    srcs = glob(["src/main/proto/*.proto"]),
    proto_java_runtime_library = ["//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.5.1:jar"],
    protoc_version = "3.0.0",  # Should match the version used in Studio.
    visibility = ["//visibility:public"],
)
