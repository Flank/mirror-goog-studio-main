load("//tools/base/bazel:gradle.bzl", "gradle_build")

gradle_build(
    name = "lib",
    output_file = "outputs/aar/library-release.aar",
    output_file_source = "outputs/aar/library-release.aar",
    build_file = "library/build.gradle",
    tasks = ["build"],
    data = [
        "//tools/base/build-system:gradle-distrib",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:support_latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//tools/buildSrc/base:version.properties",
    ] + glob(["library/**"]),
    gradle_version = "4.1",
    repos = [
        "//tools/base/bazel:offline_repo_repo.zip",
        "//tools/adt/idea/android:test_deps_repo.zip",
    ],
)
