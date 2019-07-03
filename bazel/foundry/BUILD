package(default_visibility = ["//visibility:public"])

platform(
    name = "rbe_ubuntu16",
    constraint_values = [
        "@bazel_tools//platforms:x86_64",
        "@bazel_tools//platforms:linux",
        "@bazel_tools//tools/cpp:clang",
        "@bazel_toolchains//constraints:xenial",
        "@bazel_toolchains//constraints/sanitizers:support_msan",
    ],
    remote_execution_properties = """
          properties: {
            name: "container-image"
            value:"docker://gcr.io/google.com/android-studio-alphasource/rbe-ubuntu16-as@sha256:b346bf48fd581e5445a3fe7982359a957b088c35c6b3735c0a35be0c8db6d2c1"
          }
          properties: {
            name: "dockerNetwork"
            value: "standard"
          }
          properties: {
            name: "dockerUseURandom"
            value: "true"
          }
          properties: {
            name: "dockerAddCapabilities"
            value: "SYS_NICE"
          }
         """,
)

platform(
    name = "rbe_windows1803_as",
    constraint_values = [
        "@bazel_tools//platforms:x86_64",
        "@bazel_tools//platforms:windows",
    ],
    remote_execution_properties = """
        properties: {
          name: "container-image"
          value: "docker://gcr.io/google.com/android-studio-alphasource/rbe-windows1803-as@sha256:e75260564cf7f4236867382a9d6340f25afb0afe8d65a9f65e0a748a92d1d5a3"
        }
       """,
)

# This test is run explicitly on the foundry bot as will not pass on
# a standard linux machine, because validates a very custom /dev/random condition.
java_test(
    name = "test",
    srcs = ["FoundryTest.java"],
    data = [
        "//prebuilts/studio/jdk:jdk_runtime",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//tools/base/build-system/aapt2:aapt2_for_tests",
    ],
    jvm_flags = ["-Djava.security.debug=all"],
    tags = [
        "no_test_windows",
    ],
    test_class = "FoundryTest",
    deps = [
        "//tools/base/third_party:junit_junit",
    ],
)

# CPU, disk and memory benchmark baseline tests to measure go/adt-perfgate
# foundry remote build execution pool.
java_test(
    name = "perfgate-baseline-tests",
    srcs = ["FoundryPerfgateMetrics.java"],
    # The memory test stresses memory usage, thus the jvm memory arg.
    jvm_flags = [
        "-Xms10000M",
        "-Xmx10000M",
    ],
    tags = [
        "perfgate_multi_run",
    ],
    test_class = "FoundryPerfgateMetrics",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/third_party:junit_junit",
    ],
)
