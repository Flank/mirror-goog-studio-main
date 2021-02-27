load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:bazel.bzl", "iml_module")

maven_java_library(
    name = "zipflinger",
    srcs = glob([
        "src/com/android/zipflinger/**/*.java",
    ]),
    pom = ":pom",
    visibility = [
        "//tools/base/bazel:__subpackages__",
        "//tools/base/build-system:__subpackages__",
        "//tools/base/deploy/deployer:__subpackages__",
        "//tools/base/lint:__subpackages__",
        "//tools/base/signflinger:__subpackages__",
        "//tools/base/zipflinger/tools:__subpackages__",
    ],
    deps = [
        "//tools/base/annotations",
    ],
)

maven_pom(
    name = "pom",
    artifact = "zipflinger",
    group = "com.android",
    source = "//tools/buildSrc/base:base_version",
)

java_library(
    name = "test_utils",
    srcs = [
        "test/src/java/com/android/zipflinger/AbstractZipflingerTest.java",
        "test/src/java/com/android/zipflinger/MockInputStream.java",
    ],
    deps = [
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testParsing",
    size = "small",
    srcs = [
        "test/src/java/com/android/zipflinger/ParsingTest.java",
    ],
    data = [
        "test/resource/stripped.ap_",
        "test/resource/zip_no_fd.zip",
        "test/resource/zip_with_fd.zip",
    ],
    jvm_flags = ["-Dtest.suite.jar=testParsing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testsFreeStore",
    size = "small",
    srcs = [
        "test/src/java/com/android/zipflinger/FreeStoreTest.java",
    ],
    data = [
        "test/resource/zip_no_fd.zip",
        "test/resource/zip_with_fd.zip",
    ],
    jvm_flags = ["-Dtest.suite.jar=testsFreeStore.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testsZipFlinger",
    size = "medium",
    srcs = [
        "test/src/java/com/android/zipflinger/ZipFlingerTest.java",
    ],
    data = [
        "test/resource/1-2-3files.zip",
        "test/resource/4-5files.zip",
        "test/resource/file1.txt",
        "test/resource/file2.txt",
        "test/resource/file3.txt",
        "test/resource/file4.txt",
        "test/resource/text.txt",
        "test/resource/two_files.zip",
        "test/resource/zip_with_directories.zip",
    ],
    jvm_flags = ["-Dtest.suite.jar=testsZipFlinger.jar"],
    tags = ["no_test_mac"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testZip64",
    size = "medium",
    srcs = [
        "test/src/java/com/android/zipflinger/Zip64Test.java",
    ],
    data = [
        "test/resource/5GiBFile.zip",
    ],
    jvm_flags = ["-Dtest.suite.jar=testZip64.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testsCompressor",
    size = "small",
    srcs = [
        "test/src/java/com/android/zipflinger/CompressorTest.java",
    ],
    data = [
        "test/resource/file4.txt",
    ],
    jvm_flags = ["-Dtest.suite.jar=testsCompressor.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testsMerge",
    size = "small",
    srcs = [
        "test/src/java/com/android/zipflinger/ZipMergeTest.java",
    ],
    data = [
        "test/resource/1-2-3files.zip",
        "test/resource/4-5files.zip",
    ],
    jvm_flags = ["-Dtest.suite.jar=testsMerge.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testInts",
    size = "small",
    srcs = [
        "test/src/java/com/android/zipflinger/IntsTest.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testInts.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testRepo",
    size = "small",
    srcs = [
        "test/src/java/com/android/zipflinger/RepoTest.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testRepo.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.zipflinger",
    srcs = ["src"],
    iml_files = ["android.sdktools.zipflinger.iml"],
    lint_baseline = "lint_baseline.xml",
    tags = ["no_test_mac"],
    test_data = glob(["test/resource/**"]),
    test_srcs = ["test/src/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
    ],
)
