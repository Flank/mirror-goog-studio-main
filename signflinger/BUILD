load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

maven_java_library(
    name = "signflinger",
    srcs = glob([
        "src/**/*.java",
    ]),
    pom = ":pom",
    visibility = [
        "//tools/base/build-system:__subpackages__",
        "//tools/base/signflinger/tools:__subpackages__",
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
    name = "sign_utils",
    srcs = [
        "test/src/com/android/signflinger/SignerConfig.java",
        "test/src/com/android/signflinger/Signers.java",
        "test/src/com/android/signflinger/Utils.java",
        "test/src/com/android/signflinger/Workspace.java",
    ],
    data = [
        "test/resources/AndroidManifest.xml",
        ":test_keys_and_certs",
    ],
    visibility = [
        "//tools/base/signflinger/tools:__subpackages__",
    ],
    deps = [
        ":signflinger",
        "//tools/apksig",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

filegroup(
    name = "test_keys_and_certs",
    srcs = glob([
        "test/resources/*.pem",
        "test/resources/*.pk8",
    ]),
    visibility = [
        "//tools/base/signflinger/tools:__subpackages__",
    ],
)

java_test(
    name = "benchmarkV2",
    srcs = [
        "test/src/com/android/signflinger/BenchmarkTest.java",
        "test/src/com/android/signflinger/V2Signer.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=benchmarkV2.jar"],
    tags = [
        "manual",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":sign_utils",
        ":signflinger",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testV1Signing",
    size = "medium",
    srcs = [
        "test/src/com/android/signflinger/BaseSigning.java",
        "test/src/com/android/signflinger/V1SigningTest.java",
    ],
    data = [
        "test/resources/1-2-3files.zip",
        "test/resources/test1.txt",
    ],
    jvm_flags = ["-Dtest.suite.jar=testV1Signing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":sign_utils",
        ":signflinger",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testV2Signing",
    size = "medium",
    srcs = [
        "test/src/com/android/signflinger/V2Signer.java",
        "test/src/com/android/signflinger/V2SigningTest.java",
    ],
    data = [
        "test/resources/test1.txt",
    ],
    jvm_flags = ["-Dtest.suite.jar=testV2Signing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":sign_utils",
        ":signflinger",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testV1andV2Signing",
    size = "medium",
    srcs = [
        "test/src/com/android/signflinger/BaseSigning.java",
        "test/src/com/android/signflinger/V1andV2SigningTest.java",
    ],
    data = [
        "test/resources/1-2-3files.zip",
        "test/resources/test1.txt",
    ],
    jvm_flags = ["-Dtest.suite.jar=testV1andV2Signing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":sign_utils",
        ":signflinger",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testV3Signing",
    size = "small",
    srcs = [
        "test/src/com/android/signflinger/V3SigningTest.java",
    ],
    data = [
        "test/resources/test1.txt",
    ],
    jvm_flags = ["-Dtest.suite.jar=testV3Signing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":sign_utils",
        ":signflinger",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testV4Signing",
    size = "small",
    srcs = [
        "test/src/com/android/signflinger/V4SigningTest.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testV4Signing.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":sign_utils",
        ":signflinger",
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)
