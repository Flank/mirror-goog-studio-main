load("//tools/base/bazel:gradle.bzl", "gradle_build")
load("//tools/base/bazel:maven.bzl", "maven_repository")

gradle_build(
    name = "lib",
    build_file = "library/build.gradle",
    data = [
        "//tools/base/build-system:gradle-distrib",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//tools/buildSrc/base:version.properties",
    ] + glob(["library/**"]),
    output_file = "outputs/aar/library-release.aar",
    output_file_source = "outputs/aar/library-release.aar",
    repo_zips = [
        "//tools/base/build-system:android_gradle_plugin.zip",
    ],
    repos = [
        ":test_deps",
        "//tools/base:agp_dependencies",
        "//tools/base:agp_artifacts",
    ],
    tags = ["no_windows"],  # There is a bug in aapt2 that breaks with bazel paths (>260 chars)
    tasks = ["build"],
)

maven_repository(
    name = "test_deps",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:com.android.support.appcompat-v7_28.0.0",
        "@maven//:com.android.support.test.runner_1.0.2",
        "@maven//:junit.junit_4.12",
    ],
)
