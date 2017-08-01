java_library(
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
        "//tools/base/third_party:net.sf.jopt-simple_jopt-simple",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)

java_test(
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
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
