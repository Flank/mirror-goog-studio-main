package(default_visibility = ["//visibility:public"])

java_test(
    name = "agent_tests",
    srcs = glob(["src/test/**/*"]),
    jvm_flags = ["-Dtest.suite.jar=agent_tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":agent",
        ":fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)

java_library(
    name = "agent",
    srcs = glob(["src/main/**/*.java"]),
    deps = [
        ":fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
    ],
)

java_library(
    name = "fake_android",
    srcs = glob(["src/fake-android/**/*.java"]),
    deps = ["//prebuilts/studio/sdk:platforms/latest_jar"],
)
