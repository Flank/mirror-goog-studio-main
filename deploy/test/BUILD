load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load(":swap_test.bzl", "swap_test")

package(default_testonly = True)

swap_test(
    name = "AgentBasedClassRedefinerDesugarTest",
    srcs = ["java/com/android/tools/deploy/swapper/AgentBasedClassRedefinerDesugarTest.java"],
)

swap_test(
    name = "AgentBasedClassRedefinerFailureTest",
    srcs = ["java/com/android/tools/deploy/swapper/AgentBasedClassRedefinerFailureTest.java"],
)

swap_test(
    name = "AgentBasedClassRedefinerKotlinTest",
    srcs = ["java/com/android/tools/deploy/swapper/AgentBasedClassRedefinerKotlinTest.java"],
)

swap_test(
    name = "AgentBasedClassRedefinerSimpleTest",
    srcs = ["java/com/android/tools/deploy/swapper/AgentBasedClassRedefinerSimpleTest.java"],
)

swap_test(
    name = "DexArchiveComparatorTest",
    srcs = ["java/com/android/tools/deploy/swapper/DexArchiveComparatorTest.java"],
)

swap_test(
    name = "JdiBasedClassRedefinerTest",
    srcs = ["java/com/android/tools/deploy/swapper/JdiBasedClassRedefinerTest.java"],
)

dex_library(
    name = "test-app",
    dexer = "D8",
    # Test dex compiles with a non-release build.
    # Also make it desugar as much as possible with API 23.
    flags = [
        "--debug",
        "--min-api 23",
    ],
    jars = [":test-app_java_deploy.jar"],
)

java_binary(
    name = "test-app_java",
    srcs = glob(["java/com/android/tools/deploy/swapper/testapp/**/*.java"]),
    create_executable = 0,
    deps = [
        ":test-app-kotlin-lib_java",
        "//tools/base/fakeandroid:android-mock",
    ],
)

dex_library(
    name = "test-app-swap",
    dexer = "D8",
    flags = [
        "--file-per-class",
        "--debug",
        "--min-api 23",
    ],
    jars = [":test-app-swap_java_deploy.jar"],
)

java_binary(
    name = "test-app-swap_java",
    srcs =
        glob(["test_swap/com/android/tools/deploy/swapper/testapp/**/*.java"]) + [
            "java/com/android/tools/deploy/swapper/testapp/TestActivity.java",
        ],
    create_executable = 0,
    deps = [
        ":test-app-kotlin-lib-swap_java",
        "//tools/base/fakeandroid:android-mock",
    ],
)

kotlin_library(
    name = "test-app-kotlin-lib_java",
    srcs = ["kotlin"],
)

kotlin_library(
    name = "test-app-kotlin-lib-swap_java",
    srcs = ["kotlin_swap"],
)
