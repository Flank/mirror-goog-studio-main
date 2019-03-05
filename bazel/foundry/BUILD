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
            value:"docker://gcr.io/google.com/android-studio-alphasource/rbe-ubuntu16-as@sha256:c8611373581bb39ee5320cdddb03a5c47641ff780bbc0b6cf9c812d8de86cd42"
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
        "manual",
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
    test_class = "FoundryPerfgateMetrics",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/third_party:junit_junit",
    ],
)
