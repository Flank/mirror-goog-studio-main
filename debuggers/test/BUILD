load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load(":agent_test.bzl", "agent_test")

package(default_testonly = True)

agent_test(
    name = "CoroutineDebuggerAgentTest",
    srcs = ["tests/com/android/tools/debuggers/CoroutineDebuggerAgentTest.java"],
)

dex_library(
    name = "app_dex",
    dexer = "D8",
    # Test dex compiles with a non-release build.
    # Also make it desugar as much as possible with API 23.
    flags = [
        "--debug",
        "--min-api 23",
    ],
    jars = [":app_java_deploy.jar"],
)

java_binary(
    name = "app_java",
    create_executable = 0,
    runtime_deps = [
        ":app_kotlin",
    ],
)

kotlin_library(
    name = "app_kotlin",
    testonly = True,
    srcs = glob([
        "data/app/*.kt",
    ]),
    deps = [
        ":kotlinx-coroutines-1.5.2",
        "//tools/base/fakeandroid:android-mock",
    ],
)

java_import(
    name = "kotlinx-coroutines-1.5.2",
    jars = [
        "libs/kotlinx-coroutines-core-jvm-1.5.2.jar",
        "libs/kotlinx-coroutines-debug-1.5.2.jar",
    ],
)
