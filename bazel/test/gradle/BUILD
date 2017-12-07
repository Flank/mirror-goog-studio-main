load("//tools/base/bazel:gradle.bzl", "gradle_build")

gradle_build(
    name = "lib",
    build_file = "library/build.gradle",
    data = [
        "//tools/base/build-system:gradle-distrib",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:support_latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//tools/buildSrc/base:version.properties",
    ] + glob(["library/**"]),
    gradle_version = "4.4-rc-3",
    output_file = "outputs/aar/library-release.aar",
    output_file_source = "outputs/aar/library-release.aar",
    repos = [
        "//tools/base/build-system:gradle_plugin_repo.zip",
        "//tools/adt/idea/android:test_deps.zip",
    ],
    tasks = ["build"],
)
