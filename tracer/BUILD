load("//tools/base/bazel:utils.bzl", "java_jarjar")

java_library(
    name = "tools.tracer",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
)

java_binary(
    name = "agent_binary",
    srcs = glob(["agent/src/**/*.java"]),
    deploy_manifest_lines = [
        "Premain-Class: com.android.tools.tracer.agent.TraceAgent",
        "Boot-Class-Path: trace_agent.jar",
    ],
    main_class = "com.android.tools.tracer.agent.TraceAgent",
    deps = [
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
    ],
)

# Note that the code depends at runtime on the name "trace_agent.jar"
java_jarjar(
    name = "trace_agent",
    srcs = [
        ":agent_binary_deploy.jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

java_binary(
    name = "trace_test",
    srcs = glob(["agent/testSrc/com/android/tools/tracer/**/*.java"]),
    main_class = "com.android.tools.tracer.MainTest",
    deps = [
        ":tools.tracer",
        "//tools/base/annotations",
    ],
)

sh_test(
    name = "test_agent",
    size = "small",
    srcs = [
        "agent/testSrc/test_agent.sh",
    ],
    data = [
        "agent/testSrc/com/android/tools/tracer/test.profile",
        ":trace_agent",
        ":trace_test",
    ],
    tags = [
        "no_windows",
    ],
)

load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.tracer",
    srcs = ["src"],
    iml_files = ["android.sdktools.tracer.iml"],
    visibility = ["//visibility:public"],
)
