java_library(
    name = "instant-run-server",
    srcs = glob(["src/main/java/**/*.java"]),
    javacopts = [
        "-source 6",
        "-target 6",
    ],
    resource_jars = [
        "//tools/base/instant-run/instant-run-annotations",
        "//tools/base/instant-run:instant-run-common",
        "//tools/base/instant-run/instant-run-runtime",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/annotations",
        "//tools/base/instant-run:instant-run-common",
        "//tools/base/instant-run/instant-run-runtime",
    ],
)
