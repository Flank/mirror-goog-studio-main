load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/fakeandroid:fakeandroid.bzl", "fake_android_test")

package(default_testonly = True)

java_library(
    name = "libhotswap",
    srcs = glob(["src/main/com/android/tools/deploy/swapper/*.java"]),
    deps = ["//tools/base/bazel:langtools"],
)

fake_android_test(
    name = "BasicTest",
    size = "medium",
    srcs = glob(["src/test/com/android/tools/deploy/swapper/*.java"]),
    data = [
        ":test-app",
        "//tools/base/deploy/agent/native:libswap.so",
    ],
    jvm_flags = [
        # Location of the inital test app.
        "-Dapp.dex.location=$(location :test-app)",

        # Location of the dex files to be swapped in.
        "-Dapp.swap.dex.location=$(location :test-app-swap)",

        # JVMTI Agent for the host.
        "-Dswap.agent.location=$(location //tools/base/deploy/agent/native:libswap.so)",
    ],
    deps = [
        ":libhotswap",
        ":test-app",
        ":test-app-swap",
        "//tools/base/bazel:langtools",
        "//tools/base/deploy/agent/native:libswap.so",
        "//tools/base/fakeandroid",
        "//tools/idea/.idea/libraries:JUnit4",
    ],
)

dex_library(
    name = "test-app",
    dexer = "D8",
    jars = [":test-app_java_deploy.jar"],
)

java_binary(
    name = "test-app_java",
    srcs = glob(["src/test/com/android/tools/deploy/swapper/testapp/**/*.java"]),
    create_executable = 0,
    deps = ["//tools/base/fakeandroid:android-mock"],
)

dex_library(
    name = "test-app-swap",
    dexer = "D8",
    flags = ["--file-per-class"],
    jars = [":test-app-swap_java_deploy.jar"],
)

java_binary(
    name = "test-app-swap_java",
    srcs = glob(["src/test_swap/com/android/tools/deploy/swapper/testapp/**/*.java"]),
    create_executable = 0,
    deps = ["//tools/base/fakeandroid:android-mock"],
)
