load("//tools/base/bazel:utils.bzl", "java_jarjar", "archive")

java_library(
    name = "transform",
    srcs = glob(["src/main/java/**/*.java"]),
    resource_jars = [":dependencies"],
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/5.0.4:jar",
    ],
)

java_jarjar(
    name = "profilers-transform",
    srcs = [
        ":transform",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/5.0.4:jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

archive(
    name = "dependencies",
    deps = {
        "//tools/base/profiler/app:studio-profiler": "dependencies",
    },
)
