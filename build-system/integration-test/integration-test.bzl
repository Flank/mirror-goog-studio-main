load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel/validations:timeout.bzl", "APPROVED_ETERNAL_TESTS")

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
        shard_count = None,
        tags = [],
        timeout = "eternal",
        lint_baseline = None,
        **kwargs):
    lib_name = name + ".testlib"
    kotlin_library(
        name = lib_name,
        srcs = srcs,
        deps = deps,
        testonly = True,
        lint_baseline = lint_baseline,
        lint_is_test_sources = True,
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
        shard_count = None,
        maven_repos = maven_repos,
        runtime_deps = runtime_deps,
        tags = tags,
        **kwargs
    )

# Given a glob, this will create integration gradle test target for each of the sources in the glob.
def single_gradle_integration_test_per_source(
        name,
        deps,
        data,
        maven_repos,
        package_name,
        srcs,
        runtime_deps = [],
        flaky_targets = [],
        tags = [],
        **kwargs):
    split_targets = []

    # List of target names approved to use an eternal timeout.
    eternal_target_names = []
    eternal_target_prefix = "//" + package_name + ":"
    for target in APPROVED_ETERNAL_TESTS:
        if target.startswith(eternal_target_prefix):
            eternal_target_names.append(target[len(eternal_target_prefix):])

    # need case-insensitive target names because of case-insensitive FS e.g. on Windows
    lowercase_split_targets = []
    num_flaky_applied = 0
    for src in srcs:
        start_index = src.rfind("/")
        end_index = src.rfind(".")
        target_name = src[start_index + 1:end_index]
        if target_name.lower() in lowercase_split_targets:
            # prepend part of package name to make unique
            target_name = src.split("/")[-2] + "." + target_name
        split_targets.append(target_name)
        lowercase_split_targets.append(target_name.lower())
        is_flaky = target_name in flaky_targets
        if is_flaky:
            num_flaky_applied += 1

        gradle_integration_test(
            name = target_name,
            srcs = [src],
            deps = deps,
            flaky = is_flaky,
            data = data,
            shard_count = None,
            maven_repos = maven_repos,
            runtime_deps = runtime_deps,
            tags = tags,
            timeout = "eternal" if target_name in eternal_target_names else "long",
            **kwargs
        )
    if num_flaky_applied != len(flaky_targets):
        fail("mismatch between flaky_targets given and targets found.")

    native.test_suite(
        name = name,
        tests = split_targets,
    )
