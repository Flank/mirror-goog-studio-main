load("//tools/base/bazel:bazel.bzl", "iml_module")

exports_files([
    "emulator_launcher.sh.template",
])

java_library(
    name = "emulator_rule",
    srcs = [
        "src/com/android/tools/bazel/avd/Emulator.java",
    ],
    visibility = [
        "//visibility:public",
    ],
    deps = [
        "//tools/base/third_party:junit_junit",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.base.bazel.avd",
    srcs = ["src"],
    iml_files = ["android.sdktools.base.bazel.avd.iml"],
    visibility = ["//visibility:public"],
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)
