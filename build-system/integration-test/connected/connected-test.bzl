load("//tools/base/build-system/integration-test:integration-test.bzl", "single_gradle_integration_test")
load("//tools/base/bazel:maven.bzl", "maven_repository")

# A gradle connected test
#
# Usage:
# gradle_connected_test(
#     name = the name of the file containing the test(s), excluding the file extension, for example
#            "BasicConnectedTest"
#     srcs = the relative path of the file containing the test(s), excluding the file name, for
#            example "src/test/java/com/android/build/gradle/integration/connected/application/"
#     deps = test classes output
#     data = test data: SDK parts, test projects, and avd (see //tools/base/bazel/avd:avd.bzl)
#     maven_repos = Absolute targets for maven repos containing the plugin(s) under test
# )
def gradle_connected_test(
        name,
        srcs,
        avd,
        deps,
        data,
        maven_repos,
        emulator_binary_path = "prebuilts/studio/sdk/linux/emulator/emulator",
        maven_artifacts = [],
        runtime_deps = [],
        tags = [],
        timeout = "long",
        jvm_flags = [],
        **kwargs):
    if avd:
        script_path = "$(rootpath %s)" % avd
        jvm_flags = jvm_flags + [
            "-DEMULATOR_SCRIPT_PATH=%s" % script_path,
            "-DEMULATOR_BINARY_PATH=%s" % emulator_binary_path,
        ]
    if maven_artifacts:
        repo_name = name + ".mavenRepo"
        maven_repository(
            name = repo_name,
            artifacts = maven_artifacts,
        )
        absolute_path = "//%s:%s" % (native.package_name(), repo_name)
        maven_repos += [absolute_path]
    single_gradle_integration_test(
        name = name,
        srcs = srcs,
        deps = deps,
        data = data + ([avd] if avd else []),
        maven_repos = maven_repos,
        runtime_deps = runtime_deps,
        tags = tags + [
            "no_mac",
            "no_windows",
        ],
        timeout = timeout,
        jvm_flags = jvm_flags,
        **kwargs
    )
