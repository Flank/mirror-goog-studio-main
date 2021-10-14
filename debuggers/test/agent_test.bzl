load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

# a fake android test configured to test the coroutines debugger agent
def agent_test(name, srcs, app_dex):
    fake_android_test(
        name = name,
        size = "medium",
        srcs = srcs + native.glob(
            include = ["tests/com/android/tools/debuggers/infra/*.java"],
        ),
        data = [
            app_dex,
            "//tools/base/debuggers/native/coroutine/agent:coroutine_debugger_agent.so",
        ],
        jvm_flags = [
            # Location of the initial test app.
            "-Dapp.dex.location=$(location %s)" % app_dex,

            # JVMTI Agent for the host.
            "-Dswap.agent.location=$(location //tools/base/debuggers/native/coroutine/agent:coroutine_debugger_agent.so)",
        ],
        deps = [
            "//tools/base/fakeandroid",
        ],
    )
