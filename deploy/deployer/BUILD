load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library", "coverage_java_test")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.deployer",
    srcs = ["src/main/java"],
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
    exports = ["//tools/base/deploy/deployer:deploy_java_proto"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/apkparser:studio.android.sdktools.binary-resources[module]",
        "//tools/base/deploy/deployer:deploy_java_proto",
        "//prebuilts/r8",
        "//tools/base/deploy/deployer:libjava_version",
        "//tools/adt/idea/.idea/libraries:sqlite",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/base/tracer:studio.android.sdktools.tracer[module]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/base/perf-logger:studio.perf-logger[module, test]",
        "//tools/base/deploy/deployer:fake_device_proto[test]",
        "//tools/adt/idea/.idea/libraries:studio-grpc[test]",
        "//tools/base/zipflinger:studio.android.sdktools.zipflinger[module]",
        "//tools/base/deploy/deployer:libjava_sites",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/manifest-parser:studio.android.sdktools.manifest-parser[module]",
        "//tools/base/adblib:studio.android.sdktools.adblib[module]",
        "//tools/base/adblib-tools:studio.android.sdktools.adblib.tools[module]",
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
        "//tools/base/adblib",
        "//tools/base/adblib-tools:adblib.tools",
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/bazel:langtools",
        "//tools/base/bazel:studio-proto",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/base/manifest-parser:tools.manifest-parser",
        "//tools/base/tracer:tools.tracer",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:org.xerial.sqlite-jdbc",
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
    srcs = glob(["src/test/java/**/*.java"]) + [
        "//tools/base/deploy/sites:Sites.java",
    ],
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
        "//tools/base/annotations",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/deploy/installer/tests:fake_device_proto",
        "//tools/base/deploy/proto:deploy_java_proto",
        "//tools/base/fakeadbserver:tools.fakeadbserver",
        "//tools/base/manifest-parser:tools.manifest-parser",
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/tracer:tools.tracer",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
    ],
)

load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "deploy_java_proto",
    jars = ["//tools/base/deploy/proto:deploy_java_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libjava_version",
    jars = ["//tools/base/deploy/deployer:libjava_version.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "fake_device_proto",
    jars = ["//tools/base/deploy/installer/tests:fake_device_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
jvm_import(
    name = "libjava_sites",
    jars = ["//tools/base/deploy/sites:libjava_sites.jar"],
    visibility = ["//visibility:public"],
)
