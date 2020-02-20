load("//tools/base/build-system/integration-test:integration-test.bzl", "single_gradle_integration_test")

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
        deps,
        data,
        maven_repos,
        runtime_deps = [],
        tags = [],
        timeout = "long",
        **kwargs):
    single_gradle_integration_test(
        name = name,
        srcs = srcs,
        deps = deps,
        data = data,
        maven_repos = maven_repos,
        runtime_deps = runtime_deps,
        tags = tags + [
            "no_mac",
            "no_windows",
        ],
        timeout = timeout,
        **kwargs
    )
