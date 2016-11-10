# Bazel (https://bazel.io/) BUILD file for apkzlib library.

licenses(["notice"])  # Apache License 2.0

java_library(
    name = "apkzlib",
    srcs = glob([
        "src/main/java/**/*.java",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:guava-tools",
        "//tools/idea/.idea/libraries:mockito[test]",
    ],
)
