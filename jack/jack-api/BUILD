load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

maven_java_library(
    name = "jack-api",
    srcs = glob(["src/main/java/**"]),
    pom = ":pom",
    visibility = ["//visibility:public"],
    deps = [":com.google.code.findbugs_jsr305_neverlink"],
)

maven_pom(
    name = "pom",
    artifact = "jack-api",
    group = "com.android.tools.jack",
    version = "0.13.0",  # TODO: stop hardcoding the version here.
)

# Compile-time only dependency on the JSR 305 annotations.
java_library(
    name = "com.google.code.findbugs_jsr305_neverlink",
    neverlink = 1,
    exports = ["//tools/base/third_party:com.google.code.findbugs_jsr305"],
)

java_test(
    name = "tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":jack-api",
        "//tools/base/third_party:junit_junit",
    ],
)
