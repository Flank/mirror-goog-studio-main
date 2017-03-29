load("//tools/base/bazel:bazel.bzl", "iml_module", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

iml_module(
    name = "studio.java-lib-model",
    srcs = ["src/main/java"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/base/annotations:studio.android-annotations[module]"],
)

fileset(
    name = "version.properties",
    srcs = ["//tools/buildSrc/base:version.properties"],
    mappings = {"//tools/buildSrc/base:": "com/android/java/model/"},
)

maven_java_library(
    name = "java-lib-model",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/java-lib-plugin/java-lib-model",
    resources = [":version.properties"],
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//tools/base/annotations",
    ],
)

maven_pom(
    name = "pom",
    artifact = "java-lib-model",
    group = "com.android.java.tools.build",
    source = "//tools/buildSrc/base:build_version",
)
