load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.intellij.android.wizardTemplate.impl",
    srcs = ["src"],
    iml_files = ["intellij.android.wizardTemplate.impl.iml"],
    lint_baseline = "lint_baseline.xml",
    resources = ["res"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/wizard/template-plugin:studio.intellij.android.wizardTemplate.plugin[module]",
    ],
)
