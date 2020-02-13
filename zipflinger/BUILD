load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:bazel.bzl", "iml_module")

maven_java_library(
    name = "zipflinger",
    srcs = glob([
        "src/com/android/zipflinger/**/*.java",
    ]),
    pom = ":pom",
    visibility = [
        "//tools/base/build-system:__subpackages__",
        "//tools/base/deploy/deployer:__subpackages__",
        "//tools/base/lint:__subpackages__",
        "//tools/base/signflinger:__subpackages__",
    ],
    deps = [
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
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
        "test/src/java/com/android/zipflinger/TestBase.java",
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
        "test/src/java/com/android/zipflinger/TestParsing.java",
    ],
    data = glob([
        "test/resource/zip_no_fd.zip",
        "test/resource/zip_with_fd.zip",
        "test/resource/stripped.ap_",
    ]),
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
        "test/src/java/com/android/zipflinger/TestFreeStore.java",
    ],
    data = glob([
        "test/resource/zip_no_fd.zip",
        "test/resource/zip_with_fd.zip",
    ]),
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
        "test/src/java/com/android/zipflinger/TestZipFlinger.java",
    ],
    data = glob([
        "test/resource/1-2-3files.zip",
        "test/resource/4-5files.zip",
        "test/resource/file1.txt",
        "test/resource/file2.txt",
        "test/resource/file3.txt",
        "test/resource/file4.txt",
        "test/resource/text.txt",
        "test/resource/zip_with_directories.zip",
        "test/resource/two_files.zip",
    ]),
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
    name = "testsCompressor",
    size = "small",
    srcs = [
        "test/src/java/com/android/zipflinger/TestCompressor.java",
    ],
    data = glob([
        "test/resource/file4.txt",
    ]),
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
        "test/src/java/com/android/zipflinger/TestZipMerge.java",
    ],
    data = glob([
        "test/resource/1-2-3files.zip",
        "test/resource/4-5files.zip",
    ]),
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
        "test/src/java/com/android/zipflinger/TestInts.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testInts.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_binary(
    name = "verify",
    srcs = glob([
        "tools/src/com/android/zipflinger/Verify.java",
    ]),
    main_class = "com.android.zipflinger.Verify",
    deps = [
        ":zipflinger",
    ],
)

java_binary(
    name = "map",
    srcs = glob([
        "tools/src/com/android/zipflinger/Mapper.java",
    ]),
    main_class = "com.android.zipflinger.Mapper",
    deps = [
        ":zipflinger",
    ],
)

# Debug tool to create zip files directly with zipflinger
java_binary(
    name = "zip",
    srcs = glob([
        "tools/src/com/android/zipflinger/Zip.java",
    ]),
    main_class = "com.android.zipflinger.Zip",
    deps = [
        ":zipflinger",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.zipflinger",
    # do not sort: must match IML order
    srcs = [
        "src",
        "tools/src",
    ],
    iml_files = ["android.sdktools.zipflinger.iml"],
    tags = ["no_test_mac"],
    test_data = glob(["test/resource/**"]),
    test_srcs = ["test/src/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:JUnit4",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
    ],
)
