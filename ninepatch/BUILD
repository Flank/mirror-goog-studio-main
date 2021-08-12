load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.ninepatch",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.ninepatch.iml"],
    lint_baseline = "lint_baseline.xml",
    test_srcs = [
        "src/test/java",
        "src/test/resources",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
        "NOTICE",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
