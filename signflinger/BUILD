java_library(
    name = "signflinger",
    srcs = glob([
        "src/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/apksig",
        "//tools/base/annotations",
        "//tools/base/zipflinger",
    ],
)

java_library(
    name = "test_utils",
    srcs = [
        "tests/src/com/android/signflinger/SignResult.java",
        "tests/src/com/android/signflinger/Signer.java",
        "tests/src/com/android/signflinger/SignerConfig.java",
        "tests/src/com/android/signflinger/Signers.java",
        "tests/src/com/android/signflinger/Utils.java",
    ],
    deps = [
        "//tools/apksig",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
    ],
)

java_binary(
    name = "benchmarks",
    srcs = [
        "tests/src/com/android/signflinger/Benchmarks.java",
        "tests/src/com/android/signflinger/V2Signer.java",
    ],
    data =
        glob(["tests/resources/*.pem"]) + glob([
            "tests/resources/*.pk8",
        ]) + ["tests/resources/AndroidManifest.xml"],
    main_class = "signflinger.Benchmarks",
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
        "tests/src/com/android/signflinger/V2Signer.java",
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
