load("//tools/base/bazel:proto.bzl", "java_proto_library")
load("//tools/base/bazel:utils.bzl", "java_jarjar", "archive")

java_jarjar(
    name = "jarjar",
    srcs = [
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/aapt2-proto/0.3.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

java_library(
    name = "aapt-proto-jarjar",
    visibility = ["//visibility:public"],
    exports = [":jarjar"],
    runtime_deps = [":jarjar"],
)
