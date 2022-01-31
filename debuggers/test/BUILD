load(":agent_test.bzl", "agent_test")
load(":coroutines_app_dex.bzl", "coroutines_app_dex")

package(default_testonly = True)

agent_test(
    name = "CoroutineDebuggerAgentTest_1.5.2",
    srcs = ["tests/com/android/tools/debuggers/CoroutineDebuggerAgentOldLibraryTest.java"],
    app_dex = ":app_dex_1.5.2",
)

agent_test(
    name = "CoroutineDebuggerAgentTest_1.6.0",
    srcs = ["tests/com/android/tools/debuggers/CoroutineDebuggerAgentTest.java"],
    app_dex = ":app_dex_1.6.0",
)

# old versions of the library don't have META-INF/*.version file
# so we pass nothing to resources.
coroutines_app_dex(
    name = "app_dex_1.5.2",
    coroutines_libs = ":kotlinx-coroutines-1.5.2",
    meta_inf_resources = [],
)

coroutines_app_dex(
    name = "app_dex_1.6.0",
    coroutines_libs = ":kotlinx-coroutines-1.6.0",
    meta_inf_resources = [":kotlinx-coroutines-core-jvm-1.6.0-version_file"],
)

java_import(
    name = "kotlinx-coroutines-1.5.2",
    jars = [
        "libs/kotlinx-coroutines-core-jvm-1.5.2.jar",
        "libs/kotlinx-coroutines-debug-1.5.2.jar",
    ],
)

java_import(
    name = "kotlinx-coroutines-1.6.0",
    jars = [
        "libs/kotlinx-coroutines-core-jvm-1.6.0.jar",
        "libs/kotlinx-coroutines-debug-1.6.0.jar",
    ],
)

# This rule extracts META-INF/kotlinx_coroutines_core.version from the coroutine lib jar.
genrule(
    name = "kotlinx-coroutines-core-jvm-1.6.0-version_file",
    srcs = ["libs/kotlinx-coroutines-core-jvm-1.6.0.jar"],
    outs = ["1.6.0_coroutines/kotlinx_coroutines_core.version"],
    cmd = "$(location //tools/base/bazel:unzipper) $< META-INF/kotlinx_coroutines_core.version:$@",
    tools = ["//tools/base/bazel:unzipper"],
)
