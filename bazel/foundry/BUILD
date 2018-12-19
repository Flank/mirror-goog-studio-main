load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

package(default_visibility = ["//visibility:public"])

platform(
    name = "rbe_debian8",
    constraint_values = [
        "@bazel_tools//platforms:x86_64",
        "@bazel_tools//platforms:linux",
        "@bazel_tools//tools/cpp:clang",
        "@bazel_toolchains//constraints:jessie",
    ],
    remote_execution_properties = """
          properties: {
            name: "container-image"
            value:"docker://gcr.io/google.com/android-studio-alphasource/nosla-debian8-clang-as@sha256:71c886770d70dd28c1e8a8f58eab8f8ed8bc7322550ec0dcca3cf28ae6eeac19"
         }
         """,
)

# This test is run explicitly on the foundry bot as will not pass on
# a standard linux machine, because validates a very custom /dev/random condition.
coverage_java_test(
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
        "no_mac",
        "no_windows",
    ],
    test_class = "FoundryTest",
    deps = [
        "//tools/base/third_party:junit_junit",
    ],
)

# CPU, disk and memory benchmark baseline tests to measure go/adt-perfgate
# foundry remote build execution pool.
coverage_java_test(
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
