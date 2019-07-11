load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

maven_java_library(
    name = "zipflinger",
    srcs = glob([
        "src/zipflinger/**/*.java",
    ]),
    pom = ":pom",
    visibility = [
        "//tools/base/build-system:__subpackages__",
        "//tools/base/lint:__subpackages__",
        "//tools/base/signflinger:__subpackages__",
    ],
    deps = [
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/27.0.1-jre:jar",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
    ],
)

maven_pom(
    name = "pom",
    artifact = "zipflinger",
    group = "zipflinger",
    source = "//tools/buildSrc/base:base_version",
)

java_binary(
    name = "benchmarks",
    srcs = [
        "test/src/java/zipflinger/Benchmark.java",
        "test/src/java/zipflinger/BenchmarkAdd.java",
        "test/src/java/zipflinger/BenchmarkFolderMerge.java",
        "test/src/java/zipflinger/BenchmarkJarMerge.java",
        "test/src/java/zipflinger/BenchmarkList.java",
    ],
    main_class = "zipflinger.Benchmark",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/27.0.1-jre:jar",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/third_party:junit_junit",
    ],
)

java_library(
    name = "test_utils",
    srcs = [
        "test/src/java/zipflinger/StopWatch.java",
        "test/src/java/zipflinger/TestBase.java",
        "test/src/java/zipflinger/TreeCreator.java",
        "test/src/java/zipflinger/ZipCreator.java",
    ],
    deps = [
        ":zipflinger",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "testParsing",
    size = "small",
    srcs = [
        "test/src/java/zipflinger/TestParsing.java",
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
        "test/src/java/zipflinger/TestFreeStore.java",
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
    size = "small",
    srcs = [
        "test/src/java/zipflinger/TestZipFlinger.java",
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
        "test/src/java/zipflinger/TestCompressor.java",
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
        "test/src/java/zipflinger/TestZipMerge.java",
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
    name = "testsJarFlinger",
    size = "small",
    srcs = [
        "test/src/java/zipflinger/TestJarFlinger.java",
    ],
    data = glob([
        "test/resource/1-2-3files.zip",
        "test/resource/fake.apk",
    ]),
    jvm_flags = ["-Dtest.suite.jar=testsJarFlinger.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":test_utils",
        ":zipflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)
