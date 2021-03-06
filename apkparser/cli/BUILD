load("//tools/base/bazel:coverage.bzl", "coverage_java_test", "coverage_java_library")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")

coverage_java_library(
    name = "tools.analyzer-cli",
    srcs = glob(["src/main/java/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/apkparser:tools.binary-resources",
        "//tools/base/apkparser/analyzer:tools.analyzer",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "@maven//:net.sf.jopt-simple.jopt-simple",
        "@maven//:org.smali.dexlib2",
    ],
)

coverage_java_test(
    name = "tools.tests",
    srcs = glob(["src/test/java/**"]),
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.analyzer-cli",
        "//tools/base/apkparser/analyzer:tools.analyzer",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
    ],
)

sdk_java_binary(
    name = "apkanalyzer",
    default_jvm_opts = {
        "linux": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "mac": "-Dcom.android.sdklib.toolsdir=$APP_HOME",
        "win": "-Dcom.android.sdklib.toolsdir=%~dp0\\..",
    },
    main_class = "com.android.tools.apk.analyzer.ApkAnalyzerCli",
    visibility = ["//visibility:public"],
    runtime_deps = ["tools.analyzer-cli"],
)
