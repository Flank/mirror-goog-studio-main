load("//tools/base/bazel:bazel.bzl", "iml_module")

java_library(
    name = "agent_callback",
    srcs = glob([
        "src/**/*.java",
    ]),
    resource_strip_prefix = "tools/base/threading-agent-callback/resources",
    resources = glob(["resources/**"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.threading-agent-callback",
    srcs = ["src"],
    iml_files = ["android.sdktools.threading-agent-callback.iml"],
    resources = ["resources"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
    ],
)
