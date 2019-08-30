load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

maven_java_library(
    name = "signflinger",
    srcs = glob([
        "src/**/*.java",
    ]),
    pom = ":pom",
    visibility = [
        "//tools/base/build-system:__subpackages__",
    ],
    deps = [
        "//tools/base/annotations",
        "//tools/base/build-system:tools.apksig",
        "//tools/base/zipflinger",
    ],
)

maven_pom(
    name = "pom",
    artifact = "signflinger",
    group = "com.android",
    source = "//tools/buildSrc/base:base_version",
)

java_library(
    name = "test_utils",
    srcs = [
        "test/src/com/android/signflinger/SignResult.java",
        "test/src/com/android/signflinger/Signer.java",
        "test/src/com/android/signflinger/SignerConfig.java",
        "test/src/com/android/signflinger/TestBase.java",
        "test/src/com/android/signflinger/TestBaseV2.java",
    ],
    deps = [
        ":signflinger",
        "//tools/apksig",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_test(
    name = "benchmarkV2",
    srcs = [
        "test/src/com/android/signflinger/BenchmarkTest.java",
    ],
    data =
        glob(["test/resources/*.pem"]) + glob([
            "test/resources/*.pk8",
        ]) + ["test/resources/AndroidManifest.xml"],
    jvm_flags = ["-Dtest.suite.jar=benchmarkV2.jar"],
    tags = [
        "manual",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":signflinger",
        ":test_utils",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testV1Signing",
    size = "small",
    srcs = [
        "test/src/com/android/signflinger/TestBaseSigning.java",
        "test/src/com/android/signflinger/V1SigningTest.java",
    ],
    data = glob(["test/resources/*.pem"]) + glob([
        "test/resources/*.pk8",
    ]) + [
        "test/resources/AndroidManifest.xml",
        "test/resources/test1.txt",
        "test/resources/1-2-3files.zip",
    ],
    jvm_flags = ["-Dtest.suite.jar=testV1Signing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":signflinger",
        ":test_utils",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testV2Signing",
    size = "small",
    srcs = [
        "test/src/com/android/signflinger/V2SigningTest.java",
    ],
    data = glob(["test/resources/*.pem"]) + glob([
        "test/resources/*.pk8",
    ]) + [
        "test/resources/AndroidManifest.xml",
        "test/resources/test1.txt",
    ],
    jvm_flags = ["-Dtest.suite.jar=testV2Signing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":signflinger",
        ":test_utils",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testV1andV2Signing",
    size = "small",
    srcs = [
        "test/src/com/android/signflinger/TestBaseSigning.java",
        "test/src/com/android/signflinger/V1andV2SigningTest.java",
    ],
    data = glob(["test/resources/*.pem"]) + glob([
        "test/resources/*.pk8",
    ]) + [
        "test/resources/AndroidManifest.xml",
        "test/resources/test1.txt",
        "test/resources/1-2-3files.zip",
    ],
    jvm_flags = ["-Dtest.suite.jar=testV1andV2Signing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":signflinger",
        ":test_utils",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)
