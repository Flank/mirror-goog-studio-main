load(":agent_test.bzl", "agent_test")
load(":coroutines_app_dex.bzl", "coroutines_app_dex")

package(default_testonly = True)

agent_test(
    name = "CoroutineDebuggerAgentTest_1.5.2",
    srcs = ["tests/com/android/tools/debuggers/CoroutineDebuggerAgentTest.java"],
    app_dex = ":app_dex_1.5.2",
)

agent_test(
    name = "CoroutineDebuggerAgentTest_1.6.0_SNAPSHOT",
    srcs = ["tests/com/android/tools/debuggers/CoroutineDebuggerAgentTest.java"],
    app_dex = ":app_dex_1.6.0_SNAPSHOT",
)

coroutines_app_dex(
    name = "app_dex_1.5.2",
    coroutines_libs = ":kotlinx-coroutines-1.5.2",
)

coroutines_app_dex(
    name = "app_dex_1.6.0_SNAPSHOT",
    coroutines_libs = ":kotlinx-coroutines-1.6.0-SNAPSHOT",
)

java_import(
    name = "kotlinx-coroutines-1.5.2",
    jars = [
        "libs/kotlinx-coroutines-core-jvm-1.5.2.jar",
        "libs/kotlinx-coroutines-debug-1.5.2.jar",
    ],
)

# TODO(b/182023904) replace with stable version once released in October
java_import(
    name = "kotlinx-coroutines-1.6.0-SNAPSHOT",
    jars = [
        "libs/kotlinx-coroutines-core-jvm-1.6.0-SNAPSHOT.jar",
        "libs/kotlinx-coroutines-debug-1.5.2.jar",
    ],
)
