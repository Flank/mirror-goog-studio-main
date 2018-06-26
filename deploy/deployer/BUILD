#load("//tools/base/bazel:bazel.bzl")

java_library(
    name = "deployer",
    srcs = glob([
        "src/**/*.java",
    ]),
    deps = [
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
    ],
)

java_binary(
    name = "deployer.runner",
    main_class = "com.android.tools.deployer.DeployerRunner",
    runtime_deps = [
        ":deployer",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib",
    ],
)

java_test(
    name = "deployer.tests",
    srcs = glob(["test/java/**"]),
    data = glob(["test/resource/**"]),
    tags = ["no_test_windows"],  # b/77288863
    test_class = "com.android.tools.deployer.TestDeployer",
    deps = [
        ":deployer",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)
