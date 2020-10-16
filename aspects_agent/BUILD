load("//tools/base/bazel:utils.bzl", "java_jarjar")
load("//tools/base/bazel:coverage.bzl", "coverage_java_test")

java_binary(
    name = "agent_binary",
    srcs = glob(["src/main/**/*.java"]),
    deploy_manifest_lines = [
        "Agent-Class: com.android.tools.checker.agent.Agent",
        "Premain-Class: com.android.tools.checker.agent.Agent",
        "Boot-Class-Path: aspects_agent.jar",
        "Can-Retransform-Classes: true",
        "Can-Redefine-Classes: true",
    ],
    main_class = "com.android.tools.checker.agent.Agent",
    deps = [
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
    ],
)

coverage_java_test(
    name = "agent_tests",
    srcs = glob(["src/test/**/*.java"]),
    data = glob(["testData/**"]),
    jvm_flags = ["-Dtest.suite.jar=agent_tests.jar"],
    resources = glob(["binary-resources/src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":agent_binary",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
    ],
)

# Note that the code depends at runtime on the name "aspects_agent.jar"
java_jarjar(
    name = "aspects_agent",
    srcs = [
        ":agent_binary_deploy.jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
