java_library(
    name = "java_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system/gradle-experimental",
        "//tools/base/build-system/integration-test",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "tests",
    data = [
        "//tools/base:gmaven_repo.zip",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Dfile.encoding=UTF-8",
        "-Dsun.jnu.encoding=UTF-8",
        "-Dmaven.repo.local=/tmp/localMavenRepo",  # For gradle publishing, writing to ~/.m2
    ],
    tags = [
        "block-network",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = [
        ":java_tests",
    ],
)
