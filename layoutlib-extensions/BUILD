load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:utils.bzl", "fileset")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.layoutlib-extensions",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.layoutlib-extensions.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["src/main/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

# added manually to match build.gradle file
fileset(
    name = "tools.resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

kotlin_library(
    name = "tools.layoutlib-extensions",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    resources = [":tools.resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/third_party:org.jetbrains.kotlinx_kotlinx-coroutines-core",
    ],
)
