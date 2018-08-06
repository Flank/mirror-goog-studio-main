load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.deployer",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.deployer.iml"],
    test_data = glob(["src/test/resource/**"]),
    test_resources = ["src/test/resource"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/deploy/proto:java_proto",
        "//tools/idea/.idea/libraries:protobuf",
        "//prebuilts/r8:d8-master",
    ],
)

java_library(
    name = "tools.deployer",
    srcs = glob([
        "src/main/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/r8:d8-master",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/bazel:langtools",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/proto:java_proto",
    ],
)

java_binary(
    name = "deployer.runner",
    main_class = "com.android.tools.deployer.DeployerRunner",
    runtime_deps = [
        ":tools.deployer",
        "//tools/base/ddmlib:tools.ddmlib",
    ],
)

java_test(
    name = "deployer.tests",
    srcs = glob(["src/test/java/**"]),
    data = glob(["src/test/resource/**"]),
    jvm_flags = ["-Dtest.suite.jar=deployer.tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.deployer",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)
