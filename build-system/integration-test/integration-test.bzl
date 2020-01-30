load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

# A gradle integration test
#
# Usage:
# gradle_integration_test(
#     name = 'name',
#     srcs = glob(['**/*.java'], ['**/*.kt'])
#     deps = test classes output
#     data = test data: SDK parts and test projects.
#     maven_repos = Absolute targets for maven repos containing the plugin(s) under test
#     shard_count = 8)
def gradle_integration_test(
        name,
        srcs,
        deps,
        data,
        maven_repos,
        resources = [],
        runtime_deps = [],
        shard_count = 1,
        tags = [],
        timeout = "eternal",
        **kwargs):
    lib_name = name + ".testlib"
    kotlin_library(
        name = lib_name,
        srcs = srcs,
        deps = deps,
    )

    # Stringy conversion of repo to its target and file name
    # //tools/base/build-system/integration-test/application:gradle_plugin
    # to
    # //tools/base/build-system/integration-test/application:gradle_plugin_repo.zip
    # tools/base/build-system/integration-test/application/gradle_plugin_repo.zip,
    if not all([maven_repo.startswith("//") for maven_repo in maven_repos]):
        fail("All maven repos should be absolute targets.")

    zip_targets = [maven_repo + ".zip" for maven_repo in maven_repos]
    zip_file_names = ",".join([maven_repo[2:].replace(":", "/") + ".zip" for maven_repo in maven_repos])

    coverage_java_test(
        name = name,
        timeout = timeout,
        data = data + zip_targets,
        jvm_flags = [
            "-Dtest.suite.jar=" + lib_name + ".jar",
            "-Dfile.encoding=UTF-8",
            "-Dsun.jnu.encoding=UTF-8",
            "-Dmaven.repo.local=/tmp/localMavenRepo",  # For gradle publishing, writing to ~/.m2
            "-Dtest.excludeCategories=com.android.build.gradle.integration.common.category.DeviceTests,com.android.build.gradle.integration.common.category.DeviceTestsQuarantine,com.android.build.gradle.integration.common.category.OnlineTests",
            "-Dtest.android.build.gradle.integration.repos=" + zip_file_names,
        ],
        resources = resources,
        shard_count = shard_count,
        tags = [
            "block-network",
            "cpu:3",
            "gradle_integration",
            "slow",
        ] + tags,
        test_class = "com.android.build.gradle.integration.BazelIntegrationTestsSuite",
        runtime_deps = runtime_deps + [lib_name],
        **kwargs
    )

def single_gradle_integration_test(name, deps, data, maven_repos, srcs = "", runtime_deps = [], tags = [], **kwargs):
    gradle_integration_test(
        name = name,
        srcs = native.glob([srcs + name + ".java", srcs + name + ".kt"]),
        deps = deps,
        data = data,
        shard_count = 1,
        maven_repos = maven_repos,
        runtime_deps = runtime_deps,
        tags = tags,
        **kwargs
    )
