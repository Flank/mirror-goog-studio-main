java_library(
    name = "gradle-api",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/builder-model",
        "//tools/external/gradle:gradle-api.jar",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)

java_test(
    name = "tests",
    size = "small",
    srcs = glob(["src/test/java/**"]),
    resources = glob(["src/test/resources/**"]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        "//tools/base/testutils:tools.testutils",
    ],
    deps = [
        ":gradle-api",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
)
