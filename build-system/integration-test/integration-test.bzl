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
#     maven_repos = Absolute targets for maven repos containing the plugin(s) under test.
#                   The targets supplied here must produce zip files (use_zip = True or
#                   omitted for maven_repo targets).
#     maven_repo_manifests = Absolute targets for maven_repo targets that set use_zip = False.
#     shard_count = 8)
def gradle_integration_test(
        name,
        srcs,
        deps,
        data,
        maven_repos = [],
        maven_repo_manifests = [],
        resources = [],
        runtime_deps = [],
        tags = [],
        timeout = "long",
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

    # Generate the file names for all maven_repos. These will be passed to the
    # test suite in a Java property.
    # TODO (b/148081564) maven_repos should become the default for manifests and
    # zip file targets should go in maven_repo_zips once the migration to
    # manifests is complete.
    zip_file_targets = [maven_repo + ".zip" for maven_repo in maven_repos]
    manifest_file_targets = [manifest + ".manifest" for manifest in maven_repo_manifests]
    repo_file_names = ",".join([target[2:].replace(":", "/") for target in zip_file_targets + manifest_file_targets])

    # Depend on the manifest targets in addition to the manifest file targets so
    # that runfiles are included.
    maven_repo_dependencies = zip_file_targets + manifest_file_targets + maven_repo_manifests

    coverage_java_test(
        name = name,
        timeout = timeout,
        data = data + maven_repo_dependencies,
        jvm_flags = [
            "-Dtest.suite.jar=" + lib_name + ".jar",
            "-Dfile.encoding=UTF-8",
            "-Dsun.jnu.encoding=UTF-8",
            "-Dmaven.repo.local=/tmp/localMavenRepo",  # For gradle publishing, writing to ~/.m2
            "-Dtest.excludeCategories=com.android.build.gradle.integration.common.category.OnlineTests",
            "-Dtest.android.build.gradle.integration.repos=" + repo_file_names,
        ],
        resources = resources,
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
        very_flaky_targets = [],
        tags = [],
        **kwargs):
    # List of target names approved to use an eternal timeout.
    eternal_target_names = []
    eternal_target_prefix = "//" + package_name + ":"
    for target in APPROVED_ETERNAL_TESTS:
        if target.startswith(eternal_target_prefix):
            eternal_target_names.append(target[len(eternal_target_prefix):])

    split_targets = []

    # need case-insensitive target names because of case-insensitive FS e.g. on Windows
    lowercase_split_targets = []
    num_flaky_applied = 0
    for src in srcs:
        start_index = src.rfind("/")
        end_index = src.rfind(".")
        class_name = src[start_index + 1:end_index]
        if class_name.lower() in lowercase_split_targets:
            # prepend part of package name to make unique
            test_name = src.split("/")[-2] + "." + class_name
        else:
            test_name = class_name
        lowercase_split_targets.append(test_name.lower())
        is_flaky = test_name in flaky_targets
        if is_flaky:
            num_flaky_applied += 1

        # For coverage to work with the test suite, test targets need a <suite>__ prefix
        target_name = name + "__" + test_name
        split_targets.append(target_name)

        timeout = kwargs.pop("timeout", "long")
        if target_name in eternal_target_names:
            timeout = "eternal"
        gradle_integration_test(
            name = target_name,
            srcs = [src],
            deps = deps,
            flaky = is_flaky,
            data = data,
            shard_count = None,
            maven_repos = maven_repos,
            runtime_deps = runtime_deps,
            tags = tags + (["very_flaky"] if test_name in very_flaky_targets else []),
            timeout = timeout,
            **kwargs
        )
    if num_flaky_applied != len(flaky_targets):
        fail("mismatch between flaky_targets given and targets found.")

    native.test_suite(
        name = name,
        tests = split_targets,
    )
