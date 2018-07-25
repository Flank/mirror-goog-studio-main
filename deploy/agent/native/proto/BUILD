load("//tools/base/bazel:proto.bzl", "cc_grpc_proto_library", "java_proto_library")

package(default_visibility = [
    "//tools/base/deploy/agent/native:all",
    "//tools/base/deploy/swapper:__pkg__",
])

cc_grpc_proto_library(
    name = "agent_cc_proto",
    srcs = glob(["*.proto"]),
    # Doesn't build with this set to zero; unsure if bug in the bazel genrule or not.
    grpc_support = 1,
    tags = ["no_windows"],
)

java_proto_library(
    name = "agent_java_proto",
    srcs = glob(["*.proto"]),
    # Seems to be ok here.
    grpc_support = 0,
)
