load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar", "archive")

java_jarjar(
    name = "aapt-proto-jarjar",
    srcs = [
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/aapt2-proto/0.3.1:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
