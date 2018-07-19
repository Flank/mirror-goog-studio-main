load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.deployer",
    srcs = ["src"],
    iml_files = ["android.sdktools.deployer.iml"],
    test_data = glob(["test/resource/**"]),
    test_srcs = ["test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
    ],
)

java_library(
    name = "deployer",
    srcs = glob([
        "src/**/*.java",
    ]),
    deps = [
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
    ],
)

java_binary(
    name = "deployer.runner",
    main_class = "com.android.tools.deployer.DeployerRunner",
    runtime_deps = [
        ":deployer",
        "//tools/base/ddmlib:tools.ddmlib",
    ],
)

java_test(
    name = "deployer.tests",
    srcs = glob(["test/java/**"]),
    data = glob(["test/resource/**"]),
    jvm_flags = ["-Dtest.suite.jar=deployer.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":deployer",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)
