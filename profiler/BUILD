load("//tools/base/bazel:utils.bzl", "java_jarjar")

java_jarjar(
    name = "perfetto-protos",
    srcs = [
        "@perfetto_repo//:java_proto",
    ],
    rules = "//tools/base/bazel:jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

java_library(
    name = "netty-grpc-jar",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/io/netty/netty-buffer/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-http/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-http2/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-socks/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-common/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-handler-proxy/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-handler/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-resolver/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-transport/4.1.34.Final:jar",
    ],
)
