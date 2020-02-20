load("//tools/base/bazel/avd:avd.bzl", "avd")

# This test demonstrates running an emulator on RBE and using it for a Java
# test.
# Note the attribute: tags = ["no_mac", "no_windows"]. This is because emulator
# tests only run on Linux.
avd(name = "emulator_test_device")

java_test(
    name = "emulator_test",
    srcs = ["EmulatorTest.java"],
    data = [
        ":emulator_test_device",
        "//prebuilts/studio/sdk:platform-tools",
    ],
    tags = [
        "no_mac",
        "no_windows",
    ],
    test_class = "EmulatorTest",
    deps = [
        "//tools/base/bazel/avd:emulator_rule",
        "//tools/base/third_party:junit_junit",
    ],
)
