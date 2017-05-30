package(default_visibility = ["//visibility:public"])

java_library(
    name = "profiler-supportlib",
    srcs = glob(
        [
            "supportlib/src/main/**/*.java",
            "common/src/main/**/*.java",
        ],
    ),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
    ],
)
