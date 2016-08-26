java_library(
    name = "jack-api",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [":com.google.code.findbugs_jsr305_neverlink"],
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
