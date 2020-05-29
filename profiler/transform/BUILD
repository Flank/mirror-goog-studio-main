load("//tools/base/bazel:utils.bzl", "java_jarjar", "flat_archive")

java_library(
    name = "transform",
    srcs = glob(["src/main/java/**/*.java"]),
    resource_jars = [":dependencies"],
    resources = glob(["src/main/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/7.0:jar",
    ],
)

java_jarjar(
    name = "profilers-transform",
    srcs = [
        ":transform",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/7.0:jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

flat_archive(
    name = "dependencies",
    deps = {
        "//tools/base/profiler/app:studio-profiler": "dependencies",
        "//tools/base/profiler/app:perfa_okhttp_java": "dependencies",
    },
)

java_test(
    name = "test",
    srcs = glob([
        "testSrc/com/android/tools/profiler/transform/*.java",
    ]),
    data = glob(["testData/**"]),
    test_class = "com.android.tools.profiler.transform.TransformTest",
    deps = [
        ":transform",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)
