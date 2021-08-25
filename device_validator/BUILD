load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.dvlib",
    srcs = ["dvlib/src/main/java"],
    iml_files = ["dvlib/android.sdktools.dvlib.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["dvlib/src/main/resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/common:studio.android.sdktools.common"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
    ],
)

fileset(
    name = "tools.resources",
    srcs = glob(["dvlib/src/main/resources/**"]),
    mappings = {
        "dvlib/src/main/resources/": "dvlib/",
    },
)

maven_library(
    name = "tools.dvlib",
    srcs = glob([
        "dvlib/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools:dvlib:" + BASE_VERSION,
    notice = "dvlib/NOTICE",
    resource_strip_prefix = "tools/base/device_validator/dvlib",
    resources = [":tools.resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "dvlib/src/main/**/*.java",
        "dvlib/src/main/**/*.kt",
        "dvlib/src/main/resources/**",
    ]) + [
        "dvlib/build.gradle",
        "dvlib/NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
