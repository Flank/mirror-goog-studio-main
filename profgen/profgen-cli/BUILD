load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")

kotlin_library(
    name = "profgen-cli-lib",
    srcs = glob([
        "src/main/kotlin/**/*.kt",
    ]),
    deps = [
        "//tools/base/profgen/profgen",
        "@maven//:org.jetbrains.kotlinx.kotlinx-cli-jvm",
    ],
)

sdk_java_binary(
    name = "profgen",
    main_class = "com.android.tools.profgen.cli.MainKt",
    visibility = ["//visibility:public"],
    runtime_deps = ["profgen-cli-lib"],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/kotlin/**/*.kt",
    ]),
    data = glob(["testData/**"]),
    friends = [":profgen-cli-lib"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":profgen-cli-lib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.jetbrains.kotlin.kotlin-test",
    ],
)
