load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:utils.bzl", "fileset")

fileset(
    name = "resources",
    srcs = glob([
        "src/main/resources/**",
    ]),
    mappings = {
        "src/main/resources/": "",
    },
)

kotlin_library(
    name = "testlab-gradle-plugin",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    module_name = "testlab-gradle-plugin",
    resource_strip_prefix = "tools/base/firebase/testlab/testlab-gradle-plugin",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/build-system:gradle-api_neverlink",
    ],
)
