load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

maven_java_library(
    name = "jill-api",
    srcs = glob(["src/main/java/**"]),
    pom = ":pom",
    visibility = ["//visibility:public"],
    deps = [":com.google.code.findbugs_jsr305_neverlink"],
)

maven_pom(
    name = "pom",
    artifact = "jill-api",
    group = "com.android.tools.jill",
    version = "0.10.0",  # TODO: stop hardcoding the version here.
)

# Compile-time only dependency on the JSR 305 annotations.
java_library(
    name = "com.google.code.findbugs_jsr305_neverlink",
    neverlink = 1,
    exports = ["//tools/base/third_party:com.google.code.findbugs_jsr305"],
)
