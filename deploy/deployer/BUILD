load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.deployer",
    srcs = ["src/main/java"],
    bundle_data = [
        "//tools/base/deploy/installer:android",
    ],
    iml_files = ["android.sdktools.deployer.iml"],
    test_data = glob(["src/test/resource/**"]),
    test_resources = ["src/test/resource"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/idea/.idea/libraries:protobuf",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/idea/.idea/libraries:protobuf",
        "//prebuilts/r8:d8-master",
        "//tools/base/deploy/deployer:java_version",
        "//tools/idea/.idea/libraries:sqlite",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)

java_library(
    name = "java_version",
    srcs = select({
        "//tools/base/bazel:host_windows": ["src/windows/java/Version.java"],
        "//conditions:default": [":Version.java"],
    }),
    visibility = ["//visibility:public"],
)

genrule(
    name = "version",
    outs = [
        "Version.java",
    ],
    cmd = "$(location //tools/base/bazel:bin2c) -lang=java -embed=false -variable=com.android.tools.deployer.Version -output=$(location Version.java) $(locations //tools/base/deploy/installer:unversioned_installer)",
    tags = ["no_windows"],
    tools = [
        "//tools/base/bazel:bin2c",
        "//tools/base/deploy/installer:unversioned_installer",
    ],
    visibility = ["//visibility:private"],
)

java_library(
    name = "tools.deployer",
    srcs = glob([
        "src/main/**/*.java",
    ]),
    # Embed android installer in the jar (x86_64, arm7, arm64)
    resources = ["//tools/base/deploy/installer:android"],
    visibility = ["//visibility:public"],
    deps = [
        ":java_version",
        "//prebuilts/r8:d8-master",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/bazel:langtools",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/idea/.idea/libraries:sqlite",
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
    size = "small",
    srcs = glob(["src/test/java/**/*.java"]),
    data = glob(["src/test/resource/**"]),
    jvm_flags = ["-Dtest.suite.jar=deployer.tests.jar"],
    tags = ["no_test_windows"],  # b/116693861
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.deployer",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
        "//tools/base/common:tools.common",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
