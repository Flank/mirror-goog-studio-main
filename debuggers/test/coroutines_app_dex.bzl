load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# rules used to build the app's dex using a specific version of coroutine lib
def coroutines_app_dex(name, coroutines_libs):
    kotlin_library(
        name = "app_kotlin_%s" % name,
        testonly = True,
        srcs = native.glob([
            "data/app/*.kt",
        ]),
        deps = [
            coroutines_libs,
            "//tools/base/fakeandroid:android-mock",
        ],
    )

    native.java_binary(
        name = "app_java_%s" % name,
        create_executable = 0,
        runtime_deps = [
            ":app_kotlin_%s" % name,
        ],
    )

    dex_library(
        name = name,
        dexer = "D8",
        # Test dex compiles with a non-release build.
        # Also make it desugar as much as possible with API 23.
        flags = [
            "--debug",
            "--min-api 23",
        ],
        jars = [":app_java_%s_deploy.jar" % name],
    )
