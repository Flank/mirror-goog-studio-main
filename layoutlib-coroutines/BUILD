load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:utils.bzl", "fileset")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.layoutlib-coroutines",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.layoutlib-coroutines.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["src/main/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:kotlinx-coroutines-jdk8",
        "//tools/idea/.idea/libraries:kotlin-plugin",
    ],
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
    name = "tools.layoutlib-coroutines",
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
