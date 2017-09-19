java_library(
    name = "java_tests",
    srcs = glob(["**/*.java"]),
    deps = [
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/build-system/gradle-api",
        "//tools/base/build-system/gradle-core",
        "//tools/base/build-system/integration-test",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/instant-run:instant-run-client",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "tests",
    timeout = "eternal",
    # TODO: can we make this test start from no prebuilt SDK.
    # Note: this test is slightly unusual in testing the auto download, hence the hardcoded file
    # references. This is not necessarily the pattern to follow in other tests.
    data = [
        "//prebuilts/studio/sdk:build-tools/27.0.1",
        "//prebuilts/studio/sdk:ndk-bundle",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/addon2-1.xml",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/build-tools_r27.0.1-linux.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/build-tools_r27.0.1-macosx.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/build-tools_r27.0.1-windows.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/cmake-3.6.4111459-darwin-x86_64.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/cmake-3.6.4111459-linux-x86_64.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/cmake-3.6.4111459-windows-x86_64.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/platform-26_r02.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/platform-tools_r26.0.2-darwin.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/platform-tools_r26.0.2-linux.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/platform-tools_r26.0.2-windows.zip",
        "//prebuilts/studio/sdk/remote:dl.google.com/android/repository/repository2-1.xml",
        "//prebuilts/tools/common/cmake:cmake-3.8.2",
        "//tools/base/bazel:offline_repo_repo.zip",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system/integration-test:prebuilts_repo_repo.zip",
        "//tools/base/build-system/integration-test:test-projects/commonBuildScript.gradle",
        "//tools/data-binding:data_binding_runtime_repo.zip",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Dfile.encoding=UTF-8",
        "-Dsun.jnu.encoding=UTF-8",
        "-Dmaven.repo.local=/tmp/localMavenRepo",  # For gradle publishing, writing to ~/.m2
    ],
    tags = [
        "block-network",
        "no_mac",
        "no_windows",
    ],
    test_class = "com.android.build.gradle.integration.BazelIntegrationTestsSuite",
    runtime_deps = [
        ":java_tests",
    ],
)
