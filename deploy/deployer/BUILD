load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.deployer",
    srcs = ["src/main/java"],
    bundle_data = [
        "//tools/base/deploy/installer:android-installer",
    ],
    iml_files = ["android.sdktools.deployer.iml"],
    test_coverage = True,
    test_data = glob(["src/test/resource/**"]) + select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            "//tools/base/deploy/installer:test-installer",
            "//tools/base/deploy/installer:bash_bridge",
        ],
    }),
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
        "//prebuilts/r8:r8-master",
        "//tools/base/deploy/deployer:java_version",
        "//tools/idea/.idea/libraries:sqlite",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/tracer:studio.android.sdktools.tracer[module]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
    ],
)

java_library(
    name = "java_version",
    srcs = select({
        "//tools/base/bazel:remote_windows": ["src/windows/java/Version.java"],
        "//conditions:default": ["//tools/base/deploy/installer:Version.java"],
    }),
    visibility = ["//visibility:public"],
)

java_library(
    name = "tools.deployer",
    srcs = glob([
        "src/main/**/*.java",
    ]),
    # Embed android installer in the jar (x86, x86_64, arm7, arm64)
    resources = ["//tools/base/deploy/installer:android-installer"],
    tags = ["no_rbe_windows"],
    visibility = ["//visibility:public"],
    deps = [
        ":java_version",
        "//prebuilts/r8:r8-master",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/bazel:langtools",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/base/tracer:tools.tracer",
        "//tools/idea/.idea/libraries:sqlite",
    ],
)

java_binary(
    name = "deployer.runner",
    data = ["//tools/base/tracer:trace_agent"],
    jvm_flags = ["-javaagent:$(location //tools/base/tracer:trace_agent)"],
    main_class = "com.android.tools.deployer.DeployerRunner",
    tags = ["no_rbe_windows"],
    runtime_deps = [
        ":tools.deployer",
        "//tools/base/ddmlib:tools.ddmlib",
    ],
)

coverage_java_test(
    name = "deployer.tests",
    size = "medium",
    srcs = glob(["src/test/java/**/*.java"]),
    data = glob(["src/test/resource/**"]) + select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            "//tools/base/deploy/installer:test-installer",
            "//tools/base/deploy/installer:bash_bridge",
        ],
    }),
    jvm_flags = ["-Dtest.suite.jar=deployer.tests.jar"],
    tags = [
        "no_rbe_windows",
        "no_test_windows",
    ],  # b/116693861
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":java_version",
        ":tools.deployer",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/base/fakeadbserver:tools.fakeadbserver",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
