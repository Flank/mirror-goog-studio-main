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
)
