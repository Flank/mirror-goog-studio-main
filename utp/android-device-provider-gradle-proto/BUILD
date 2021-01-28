load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:maven.bzl", "maven_pom")

java_proto_library(
    name = "android-device-provider-gradle-proto",
    srcs = glob(["*.proto"]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/utp/android-device-provider-gradle-proto",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "android-device-provider-gradle-proto",
    group = "com.android.tools.utp",
    source = "//tools/buildSrc/base:base_version",
)

java_proto_library(
    name = "studio.android-device-provider-gradle-proto",
    srcs = glob(["*.proto"]),
    proto_java_runtime_library = ["//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.5.1:jar"],
    protoc_version = "3.0.0",  # Should match the version used in Studio.
    resource_strip_prefix = "tools/base/utp/android-device-provider-gradle-proto",
    visibility = ["//visibility:public"],
)
