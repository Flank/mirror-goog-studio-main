load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test", "coverage_java_library")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.deployer",
    srcs = ["src/main/java"],
    bundle_data = [
        "//tools/base/deploy/installer:android-installer",
    ],
    iml_files = ["android.sdktools.deployer.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = [
        "no_mac",
    ],
    test_data = glob(["src/test/resource/**"]) + select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            "//tools/base/deploy/installer:test-installer",
            "//tools/base/deploy/installer/tests:artifacts",
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
        "//prebuilts/r8",
        "//tools/base/deploy/deployer:java_version",
        "//tools/idea/.idea/libraries:sqlite",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:Objenesis[test]",
        "//tools/base/tracer:studio.android.sdktools.tracer[module]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/idea/.idea/libraries:studio-proto",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/base/deploy/installer/tests:fake_device_proto[test]",
        "//tools/idea/.idea/libraries:studio-grpc[test]",
        "//tools/base/zipflinger:studio.android.sdktools.zipflinger[module]",
    ],
)

java_library(
    name = "java_version",
    srcs = ["//tools/base/deploy/installer:Version.java"],
    visibility = ["//visibility:public"],
)

coverage_java_library(
    name = "tools.deployer",
    srcs = glob([
        "src/main/**/*.java",
    ]),
    # Embed android installer in the jar (x86, x86_64, arm7, arm64)
    resources = ["//tools/base/deploy/installer:android-installer"],
    visibility = ["//visibility:public"],
    deps = [
        ":java_version",
        "//prebuilts/r8",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/bazel:langtools",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/base/tracer:tools.tracer",
        "//tools/base/zipflinger:studio.android.sdktools.zipflinger",
        "//tools/idea/.idea/libraries:sqlite",
        "//tools/idea/.idea/libraries:studio-proto",
    ],
)

java_binary(
    name = "deployer.runner",
    data = ["//tools/base/tracer:trace_agent"],
    jvm_flags = ["-javaagent:$(location //tools/base/tracer:trace_agent)"],
    main_class = "com.android.tools.deployer.DeployerRunner",
    runtime_deps = [
        ":tools.deployer",
        "//tools/base/ddmlib:tools.ddmlib",
    ],
)

filegroup(
    name = "test_apks",
    srcs = glob(["src/test/resource/apks/*.apk"]),
    visibility = ["//visibility:public"],
)

coverage_java_test(
    name = "deployer.tests",
    size = "medium",
    srcs = glob(["src/test/java/**/*.java"]),
    data = glob(["src/test/resource/**"]) + select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            "//tools/base/deploy/installer:test-installer",
            "//tools/base/deploy/installer/tests:artifacts",
        ],
    }) + ["//tools/base/tracer:trace_agent"],
    jvm_flags = [
        "-Dtest.suite.jar=deployer.tests.jar",
        "-Ddashboards.enabled=true",
        "-javaagent:$(location //tools/base/tracer:trace_agent)=$(location //tools/base/deploy/deployer:src/test/resource/deployer.profile)",
    ],
    tags = [
        "no_mac",
        "no_rbe_windows",
        "no_test_windows",  # b/116693861
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":java_version",
        ":tools.deployer",
        "//prebuilts/r8",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/23.0:jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/3.4.0:jar",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/installer/tests:fake_device_proto",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/base/fakeadbserver:tools.fakeadbserver",
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/tracer:tools.tracer",
        "//tools/base/zipflinger",
        "//tools/idea/.idea/libraries:studio-grpc",
        "//tools/idea/.idea/libraries:studio-proto",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "unb.studio.android.sdktools.deployer",
    srcs = ["src/main/java"],
    bundle_data = [
        "//tools/base/deploy/installer:android-installer",
    ],
    bundled_deps = [
        "//tools/base/deploy/deployer:unb.deploy_java_proto",
        "//prebuilts/r8:unb.r8",
        "//tools/base/deploy/deployer:unb.libjava_version",
    ],
    iml_files = ["android.sdktools.deployer.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = [
        "manual",
        "no_mac",
        "unb",
    ],
    test_data = glob(["src/test/resource/**"]) + select({
        "//tools/base/bazel:windows": [],
        "//conditions:default": [
            "//tools/base/deploy/installer:test-installer",
            "//tools/base/deploy/installer/tests:artifacts",
        ],
    }),
    test_resources = ["src/test/resource"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/deploy/deployer:unb.deploy_java_proto",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:unb.studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:unb.studio.android.sdktools.ddmlib[module]",
        "//tools/base/testutils:unb.studio.android.sdktools.testutils[module, test]",
        "//tools/base/apkparser:unb.studio.android.sdktools.binary-resources[module]",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/base/deploy/deployer:unb.deploy_java_proto",
        "//tools/adt/idea/.idea/libraries:unb.protobuf",
        "//prebuilts/r8:unb.r8",
        "//tools/base/deploy/deployer:unb.libjava_version",
        "//tools/adt/idea/.idea/libraries:unb.sqlite",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/base/tracer:unb.studio.android.sdktools.tracer[module]",
        "//tools/base/fakeadbserver:unb.studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto",
        "//tools/base/perf-logger:unb.studio.perf-logger[module, test]",
        "//tools/base/deploy/deployer:unb.fake_device_proto[test]",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc[test]",
        "//tools/base/zipflinger:unb.studio.android.sdktools.zipflinger[module]",
    ],
)

# managed by go/iml_to_build [unb]
java_import(
    name = "unb.deploy_java_proto",
    jars = ["//tools/base/deploy/proto:deploy_java_proto.jar"],
    tags = [
        "manual",
        "unb",
    ],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build [unb]
java_import(
    name = "unb.libjava_version",
    jars = ["//tools/base/deploy/deployer:libjava_version.jar"],
    tags = [
        "manual",
        "unb",
    ],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build [unb]
java_import(
    name = "unb.fake_device_proto",
    jars = ["//tools/base/deploy/installer/tests:fake_device_proto.jar"],
    tags = [
        "manual",
        "unb",
    ],
    visibility = ["//visibility:public"],
)
