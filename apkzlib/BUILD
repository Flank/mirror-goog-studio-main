licenses(["notice"])  # Apache License 2.0

load("//tools/base/bazel:utils.bzl", "srcjar")

srcjar(
    name = "srcjar",
    java_library = ":apkzlib",
    visibility = ["//tools/base/build-system/builder:__pkg__"],
)

java_library(
    name = "apkzlib",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:private"],  # These sources are compiled into builder.
    deps = [
        "//tools/base/build-system:tools.apksig",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
    ],
)

java_test(
    name = "apkzlib_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":apkzlib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
        "//tools/base/third_party:org.mockito_mockito-all",
    ],
)
