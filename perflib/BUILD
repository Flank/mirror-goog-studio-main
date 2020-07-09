load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.perflib",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.perflib.iml"],
    lint_baseline = "lint_baseline.xml",
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:Trove4j",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:Objenesis[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8[test]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "unb.studio.android.sdktools.perflib",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.perflib.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = [
        "manual",
        "unb",
    ],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/ddmlib:unb.studio.android.sdktools.ddmlib[module]",
        "//tools/base/testutils:unb.studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8[test]",
    ],
)
