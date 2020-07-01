load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.chunkio-processor",
    srcs = ["src"],
    iml_files = ["android.sdktools.chunkio-processor.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/chunkio:studio.android.sdktools.chunkio[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "unb.studio.android.sdktools.chunkio-processor",
    srcs = ["src"],
    iml_files = ["android.sdktools.chunkio-processor.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    tags = [
        "manual",
        "unb",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/chunkio:unb.studio.android.sdktools.chunkio[module]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
    ],
)
