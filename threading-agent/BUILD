load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

java_binary(
    name = "agent_binary",
    srcs = glob([
        "src/**/*.java",
    ]),
    deploy_manifest_lines = [
        "Premain-Class: com.android.tools.instrumentation.threading.agent.Agent",
        "Boot-Class-Path: threading_agent.jar",
    ],
    main_class = "com.android.tools.instrumentation.threading.agent.Agent",
    deps = [
        "//tools/base/annotations",
        "//tools/base/threading-agent-callback:agent_callback",
    ],
)

# Rule to build the `threading_agent.jar` that will be injected into the target JVM.
# It creates a single jar file with no external dependencies. It also repackages
# java libraries so that their versions loaded with the java agent by the boot classloader
# do not conflict with the versions of the same libraries loaded by the system class loader.
java_jarjar(
    name = "threading_agent",
    srcs = [
        ":agent_binary_deploy.jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
