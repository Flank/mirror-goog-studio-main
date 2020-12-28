package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library")

kotlin_test(
    name = "agent_tests",
    testonly = 1,
    srcs = glob([
        "src/test/com/android/tools/agent/**/*.kt",
        "src/test/com/android/tools/agent/**/*.java",
    ]),
    data = select({
        "//tools/base/bazel:windows": [],
        "//tools/base/bazel:darwin": [":libjni-test.dylib"],
        "//conditions:default": [":libjni-test.so"],
    }),
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests.jar",
        "-Djava.library.path=tools/base/dynamic-layout-inspector/agent/transport",
    ],
    resource_strip_prefix = "tools/base/dynamic-layout-inspector/agent/transport/src/testResources",
    resources = ["src/testResources/mockito-extensions/org.mockito.plugins.MockMaker"],
    tags = ["no_windows"],  # Fails to build binary part
    test_class = "com.android.testutils.JarTestSuite",
    # do not sort: fake_android must come before latest_runtime_jar in the classpath to override small pieces of it
    deps = [
        ":agent",
        "//tools/base/dynamic-layout-inspector/agent/fake-android:fake_android",
        "//prebuilts/studio/sdk:platforms/latest_runtime_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//tools/base/bazel:studio-grpc",
        "//tools/base/bazel:studio-proto",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/transport/proto:transport_java_proto",
        "//tools/base/transport/test-framework",
    ],
)

coverage_java_library(
    name = "agent",
    srcs = glob(["src/main/**/*.java"]),
    # do not sort: fake_android must come before latest_runtime_jar in the classpath to override small pieces of it
    deps = [
        "//tools/base/dynamic-layout-inspector/agent/fake-android:fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
    ],
)

cc_library(
    name = "agent_command",
    srcs = [
        "src/main/native/commands/layoutinspector_agent_command.cc",
    ],
    hdrs = [
        "src/main/native/commands/layoutinspector_agent_command.h",
    ],
    copts = ANDROID_COPTS + [
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/agent:jvmti_helper",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)

cc_library(
    name = "jni",
    srcs = [
        "src/main/native/component_tree_java_jni.cc",
        "src/main/native/compose_tree_java_jni.cc",
        "src/main/native/layoutinspector_java_jni.cc",
        "src/main/native/properties_java_jni.cc",
        "src/main/native/resource_configuration_java_jni.cc",
    ],
    copts = ANDROID_COPTS + [
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/agent:jni_wrappers",
        "//tools/base/transport/native/agent:jvmti_helper",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)

cc_library(
    name = "agent_test_support",
    srcs = ["src/test/native/testing/component_tree_test_java_jni.cc"],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/dynamic-layout-inspector/proto:layout_inspector_skia_cc_proto",
        "//tools/base/transport/proto:cc_proto",
        "//tools/base/transport/test-framework:native_test_support",
    ],
    alwayslink = 1,
)

# TODO: Investigate if we can make a macro that is covering all platforms
cc_binary(
    name = "libjni-test.dylib",
    linkshared = 1,
    tags = [
        "no_linux",
        "no_windows",
    ],
    deps = [
        ":agent_test_support",
        ":jni",
    ],
)

cc_binary(
    name = "libjni-test.so",
    linkshared = 1,
    tags = [
        "no_mac",
        "no_windows",
    ],
    deps = [
        ":agent_test_support",
        ":jni",
    ],
)
