load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.dvlib",
    # do not sort: must match IML order
    srcs = ["dvlib/src/main/java"],
    iml_files = ["dvlib/android.sdktools.dvlib.iml"],
    resources = ["dvlib/src/main/resources"],
    visibility = ["//visibility:public"],
    exports = ["//tools/base/common:studio.android.sdktools.common"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/idea/.idea/libraries:KotlinJavaRuntime",
    ],
)

fileset(
    name = "tools.resources",
    srcs = glob(["dvlib/src/main/resources/**"]) + ["dvlib/NOTICE"],
    mappings = {
        "dvlib/src/main/resources/": "dvlib/",
    },
)

maven_java_library(
    name = "tools.dvlib",
    srcs = glob(["dvlib/src/main/java/**"]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/device_validator/dvlib",
    resources = [":tools.resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
    ],
)

maven_pom(
    name = "pom",
    artifact = "dvlib",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)
