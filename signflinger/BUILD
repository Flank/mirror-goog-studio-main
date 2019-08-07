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
        "tests/src/com/android/signflinger/SignResult.java",
        "tests/src/com/android/signflinger/Signer.java",
        "tests/src/com/android/signflinger/SignerConfig.java",
        "tests/src/com/android/signflinger/TestBase.java",
        "tests/src/com/android/signflinger/TestBaseV2.java",
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
        "tests/src/com/android/signflinger/Benchmarks.java",
    ],
    data =
        glob(["tests/resources/*.pem"]) + glob([
            "tests/resources/*.pk8",
        ]) + ["tests/resources/AndroidManifest.xml"],
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
        "tests/src/com/android/signflinger/TestBaseSigning.java",
        "tests/src/com/android/signflinger/TestV1Signing.java",
    ],
    data = glob(["tests/resources/*.pem"]) + glob([
        "tests/resources/*.pk8",
    ]) + [
        "tests/resources/AndroidManifest.xml",
        "tests/resources/test1.txt",
        "tests/resources/1-2-3files.zip",
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
        "tests/src/com/android/signflinger/TestV2Signing.java",
    ],
    data = glob(["tests/resources/*.pem"]) + glob([
        "tests/resources/*.pk8",
    ]) + [
        "tests/resources/AndroidManifest.xml",
        "tests/resources/test1.txt",
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
        "tests/src/com/android/signflinger/TestBaseSigning.java",
        "tests/src/com/android/signflinger/TestV1andV2Signing.java",
    ],
    data = glob(["tests/resources/*.pem"]) + glob([
        "tests/resources/*.pk8",
    ]) + [
        "tests/resources/AndroidManifest.xml",
        "tests/resources/test1.txt",
        "tests/resources/1-2-3files.zip",
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
