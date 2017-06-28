load("//tools/base/bazel:android.bzl", "dex_library")

package(default_visibility = ["//visibility:public"])

java_library(
    name = "studio-profiler",
    srcs = glob([
        "supportlib/src/main/**/*.java",
        "common/src/main/**/*.java",
    ]),
    resource_jars = [
        "//tools/base/profiler/native/agent:supportjni",
    ],
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

java_library(
    name = "perfa_java",
    srcs = glob([
        "perfa/src/main/**/*.java",
        "common/src/main/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

dex_library(
    name = "perfa",
    jars = [":perfa_java"],
)
