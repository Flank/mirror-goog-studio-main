load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/sdklib:sdk.bzl", "sdk_java_binary")

kotlin_library(
    name = "profgen-cli-lib",
    srcs = glob([
        "src/main/kotlin/**/*.kt",
    ]),
    deps = [
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-cli-jvm/0.3.1:jar",
        "//tools/base/profgen/profgen",
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
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.42:jar",
        "//prebuilts/tools/common/m2/repository/junit/junit/4.12:jar",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-test",
    ],
)
