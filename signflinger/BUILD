java_library(
    name = "signflinger",
    srcs = glob([
        "src/signflinger/**/*.java",
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
        "tests/src/signflinger/SignResult.java",
        "tests/src/signflinger/Signer.java",
        "tests/src/signflinger/SignerConfig.java",
        "tests/src/signflinger/Signers.java",
        "tests/src/signflinger/Utils.java",
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
        "tests/src/signflinger/Benchmarks.java",
        "tests/src/signflinger/V2Signer.java",
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
    name = "testV2Signing",
    size = "small",
    srcs = [
        "tests/src/signflinger/TestV2Signing.java",
        "tests/src/signflinger/V2Signer.java",
    ],
    data = glob(["tests/resources/*.pem"]) + glob([
        "tests/resources/*.pk8",
    ]) + [
        "tests/resources/AndroidManifest.xml",
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
