load("//tools/base/build-system/integration-test:integration-test.bzl", "gradle_integration_test")

gradle_integration_test(
    name = "sdk_tests",
    # Note: this test is slightly unusual in testing the auto download, hence the hardcoded file
    # references. This is not necessarily the pattern to follow in other tests.
    srcs = glob(["**/*.java"]),
    data = [
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/3534162-studio.sdk-patcher.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/addon2-3.xml",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/android-ndk-r23-beta4-linux-x86_64.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/build-tools_r30.0.3-linux.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/cmake-3.22.1-linux.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/google_apis-24_r1.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/platform-31_r01.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/platform-tools_r31.0.3-linux.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/repository2-3.xml",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:test-projects/commonBuildScript.gradle",
    ],
    maven_repo_zips = [
        "//tools/base/build-system:android_gradle_plugin",
    ],
    maven_repos = [
        "//tools/base/build-system:android_gradle_plugin_runtime_dependencies",
        "//tools/base/build-system/integration-test/application:prebuilts",
    ],
    tags = [
        "no_test_windows",  # b/73306170
    ],
    deps = [
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-api",
        "//tools/base/build-system/gradle-core",
        "//tools/base/build-system/integration-test/framework",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)
