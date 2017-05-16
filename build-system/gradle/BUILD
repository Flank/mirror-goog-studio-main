load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

fileset(
    name = "resources",
    srcs = ["//tools/buildSrc/base:version.properties"] + glob(["src/main/resources/**"]),
    mappings = {
        "//tools/buildSrc/base:": "com/android/build/gradle/internal/",
        "src/main/resources/": "",
    },
)

maven_java_library(
    name = "gradle",
    srcs = glob(["src/main/java/**/*.java"]),
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/gradle",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api_neverlink",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-api",
        "//tools/base/build-system/gradle-core",
        "//tools/base/common:tools.common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/data-binding:tools.compilerCommon",
    ],
)

maven_pom(
    name = "pom",
    artifact = "gradle",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

java_test(
    name = "tests",
    srcs = glob(["src/test/java/**/*.java"]),
    data = [
        "//prebuilts/studio/sdk:add-ons/addon-google_apis-google-latest",
        "//prebuilts/studio/sdk:build-tools/25.0.0",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-24",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        # TODO: Make it easier to configure AndroidLocation per project.
        "-DANDROID_SDK_HOME=/tmp/android_sdk_home",
    ],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    # Make sure we're running against JUnit 4.12. See https://github.com/bazelbuild/bazel/issues/2146.
    runtime_deps = [
        "//tools/base/third_party:junit_junit",
    ],
    deps = [
        ":gradle",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-core",
        "//tools/base/common:tools.common",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
    ],
)
