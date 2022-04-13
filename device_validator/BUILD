load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/common:version.bzl", "BASE_VERSION")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.dvlib",
    srcs = ["dvlib/src/main/java"],
    iml_files = ["dvlib/android.sdktools.dvlib.iml"],
    jvm_target = "8",
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

maven_library(
    name = "tools.dvlib",
    srcs = glob([
        "dvlib/src/main/java/**/*.java",
    ]),
    coordinates = "com.android.tools:dvlib:" + BASE_VERSION,
    description = "A Library to manage the Android device database XML files.",
    notice = "dvlib/NOTICE",
    resource_strip_prefix = "tools/base/device_validator/dvlib/src/main/resources",
    resources = glob(["dvlib/src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
    ],
)
