load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.android-annotations",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.android-annotations.iml"],
    visibility = ["//visibility:public"],
    deps = ["//tools/idea/.idea/libraries:KotlinJavaRuntime"],
)

maven_java_library(
    name = "annotations",
    srcs = glob(["src/main/java/**"]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/annotations",
    visibility = ["//visibility:public"],
)

maven_pom(
    name = "pom",
    artifact = "annotations",
    group = "com.android.tools",
    source = "//tools/buildSrc/base:base_version",
)
