load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load(":swap_test.bzl", "swap_test")

package(default_testonly = True)

swap_test(
    name = "AgentBasedClassRedefinerDesugarTest",
    srcs = ["java/com/android/tools/deployer/AgentBasedClassRedefinerDesugarTest.java"],
)

swap_test(
    name = "AgentBasedClassRedefinerFailureTest",
    srcs = ["java/com/android/tools/deployer/AgentBasedClassRedefinerFailureTest.java"],
)

swap_test(
    name = "AgentBasedClassRedefinerKotlinTest",
    srcs = ["java/com/android/tools/deployer/AgentBasedClassRedefinerKotlinTest.java"],
)

swap_test(
    name = "AgentBasedClassRedefinerSimpleTest",
    srcs = ["java/com/android/tools/deployer/AgentBasedClassRedefinerSimpleTest.java"],
)

swap_test(
    name = "JdiBasedClassRedefinerTest",
    srcs = ["java/com/android/tools/deployer/JdiBasedClassRedefinerTest.java"],
)

java_test(
    name = "D8DexSplitterTest",
    srcs = ["java/com/android/tools/deployer/D8DexSplitterTest.java"],
    data = [
        "//tools/base/deploy/test/data/apk1:apk",
        "//tools/base/deploy/test/data/apk2:apk",
    ],
    jvm_flags = [
        "-Dapk1.location=$(location //tools/base/deploy/test/data/apk1:apk)",
        "-Dapk2.location=$(location //tools/base/deploy/test/data/apk2:apk)",
    ],

    # Does not seem to be able to locate
    # the two data dependencies on Windows.
    tags = ["no_windows"],
    deps = [
        "//tools/base/deploy/deployer:tools.deployer",
        "//tools/base/third_party:junit_junit",
    ],
)

dex_library(
    name = "original_dex",
    dexer = "D8",
    # Test dex compiles with a non-release build.
    # Also make it desugar as much as possible with API 23.
    flags = [
        "--debug",
        "--min-api 23",
    ],
    jars = [":original_java_deploy.jar"],
)

java_binary(
    name = "original_java",
    srcs = glob(["data/java/*.java"]),
    create_executable = 0,
    deps = [
        ":original_kotlin",
        "//tools/base/fakeandroid:android-mock",
    ],
)

kotlin_library(
    name = "original_kotlin",
    srcs = ["data/kotlin"],
    deps = [
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core-common/1.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.0.0:jar",
    ],
)

dex_library(
    name = "swapped_dex",
    dexer = "D8",
    flags = [
        "--file-per-class",
        "--debug",
        "--min-api 23",
    ],
    jars = [":swapped_java_deploy.jar"],
)

java_binary(
    name = "swapped_java",
    srcs = glob(["data/java_swap/*.java"]),
    create_executable = 0,
    deps = [
        ":swapped_kotlin",
        "//tools/base/fakeandroid:android-mock",
    ],
)

kotlin_library(
    name = "swapped_kotlin",
    srcs = ["data/kotlin_swap"],
    deps = [
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core-common/1.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.0.0:jar",
    ],
)
