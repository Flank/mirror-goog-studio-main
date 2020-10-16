load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "studio.intellij.android.wizardTemplate.plugin",
    srcs = ["src"],
    iml_files = ["intellij.android.wizardTemplate.plugin.iml"],
    lint_baseline = "lint_baseline.xml",
    project = "unb",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
    ],
)
