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
        "no_test_mac",
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
    test_class = "FoundryPerfgateMetrics",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/third_party:junit_junit",
    ],
)
