load("//tools/base/bazel:bazel.bzl", "java_jarjar")

# Studio cannot handle proto3, so we jarjar our proto library together with grpc
# on a renamed namespace.

#The long list is as we want to jar-jar specific jars, and not all of their depenendencies,
#as those are already present on the studio classpath.
java_jarjar(
    name = "studio-profiler-grpc-1.0-jarjar",
    srcs = [
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.0.0:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-auth/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-context/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-core/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-netty/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-okhttp/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-protobuf-lite/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-protobuf-nano/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-protobuf/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-stub/1.0.3:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-buffer/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-http/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-http2/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-socks/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-common/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-handler-proxy/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-handler/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-resolver/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-transport/4.1.6.Final:jar",
        "//tools/base/profiler/proto:libprofiler_java_proto.jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

java_library(
    name = "netty-grpc-jar",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/io/netty/netty-buffer/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-http/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-http2/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-socks/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-common/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-handler-proxy/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-handler/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-resolver/4.1.6.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-transport/4.1.6.Final:jar",
    ],
)
