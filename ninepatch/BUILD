load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "studio.android.sdktools.ninepatch",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.ninepatch.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    test_srcs = [
        "src/test/java",
        "src/test/resources",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
    ],
)
