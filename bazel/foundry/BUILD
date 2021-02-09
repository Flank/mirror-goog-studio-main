load(
    "@bazel_toolchains//rules/exec_properties:exec_properties.bzl",
    "create_rbe_exec_properties_dict",
)

package(default_visibility = ["//visibility:public"])

platform(
    name = "rbe_ubuntu18",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
        "@bazel_tools//tools/cpp:clang",
        "@bazel_toolchains//constraints:xenial",
        "@bazel_toolchains//constraints/sanitizers:support_msan",
    ],
    exec_properties = create_rbe_exec_properties_dict(
        container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-ubuntu18-as@sha256:b472f2ff3080b759d9abd112930170034603f8301cbce25e609be4733f5c0c74",
        docker_add_capabilities = "SYS_NICE",
        docker_network = "off",
        docker_privileged = True,  # This is required to support emulators/nested KVM.
        docker_use_urandom = True,
        labels = {"machine-size": "default"},
        os_family = "Linux",
    ),
)

platform(
    name = "rbe_windows2019_as",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:windows",
    ],
    exec_properties = create_rbe_exec_properties_dict(
        container_image = "docker://gcr.io/google.com/android-studio-alphasource/rbe-windows2019-as@sha256:64a7c97ac08d75b9efde179993adb55986869369af320d8fbe1b4dc16e678805",
        labels = {"machine-size": "default"},
        os_family = "Windows",
    ),
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
    test_class = "FoundryPerfgateMetrics",
    deps = [
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/third_party:junit_junit",
    ],
)
