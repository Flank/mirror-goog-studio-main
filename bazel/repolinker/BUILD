load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.base.bazel.repolinker",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.base.bazel.repolinker.iml"],
    test_srcs = ["src/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:truth[test]",
    ],
)

java_library(
    name = "repo_linker",
    srcs = ["src/main/java/com/android/tools/bazel/repolinker/RepoLinker.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/third_party:org.apache.maven_maven-model-builder",
    ],
)

java_test(
    name = "repo_linker_test",
    srcs = ["src/test/java/com/android/tools/bazel/repolinker/RepoLinkerTest.java"],
    test_class = "com.android.tools.bazel.repolinker.RepoLinkerTest",
    deps = [
        ":repo_linker",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
    ],
)
