package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:android.bzl", "ANDROID_COPTS")
load("//tools/base/bazel:utils.bzl", "flat_archive")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")

kotlin_test(
    name = "agent_tests",
    testonly = 1,
    srcs = glob([
        "src/test/**/*.kt",
        "src/test/**/*.java",
    ]),
    data = select({
        "windows": [],
        "mac": [":libjni-test.dylib"],
        "//conditions:default": [":libjni-test.so"],
    }) + ["//tools/base/dynamic-layout-inspector/src/testData"],
    jvm_flags = [
        "-Dtest.suite.jar=agent_tests.jar",
        "-Djava.library.path=tools/base/dynamic-layout-inspector",
    ],
    resource_strip_prefix = "tools/base/dynamic-layout-inspector/src/testResources",
    resources = ["src/testResources/mockito-extensions/org.mockito.plugins.MockMaker"],
    tags = ["no_windows"],  # Fails to build binary part
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":agent",
        ":fake_android",
        "//prebuilts/studio/sdk:platforms/latest_runtime_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/transport/test-framework",
        "//tools/idea/.idea/libraries:studio-grpc",
        "//tools/idea/.idea/libraries:studio-proto",
        "//tools/idea/.idea/libraries:transport-proto",
    ],
)

java_library(
    name = "agent",
    srcs = glob(["src/main/**/*.java"]),
    deps = [
        ":fake_android",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
    ],
)

kotlin_library(
    name = "fake_android",
    srcs = glob([
        "src/fake-android/**/*.kt",
        "src/fake-android/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//prebuilts/tools/common/m2/repository/androidx/annotation/annotation/1.1.0:jar",
    ],
)

config_setting(
    name = "windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "mac",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

cc_binary(
    name = "skia-grpc-server",
    srcs = glob([
        "src/main/native/skia/*.cc",
        "src/main/native/skia/*.h",
    ]),
    copts = [
        "-DSK_USER_CONFIG_HEADER=\\\"StudioConfig.h\\\"",
        "-UFORTIFY_SOURCE",
        "-DATRACE_TAG=ATRACE_TAG_VIEW",
        "-DSKIA_IMPLEMENTATION=1",
        "-DSK_PRINT_CODEC_MESSAGES",
        "-D_FORTIFY_SOURCE=1",
    ] + select({
        "windows": [],
        "//conditions:default": [
            "-std=c++14",
            "-Wno-implicit-fallthrough",
            "-Wno-missing-field-initializers",
            "-Wno-thread-safety-analysis",
            "-Wno-unused-parameter",
            "-Wno-unused-variable",
            "-fvisibility=hidden",
            "-fexceptions",
            "-mssse3",
        ],
    }),
    includes = ["src/main/native/skia"],
    linkopts = select({
        "windows": ["Ws2_32.lib"],
        "//conditions:default": ["-ldl"],
    }),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/dynamic-layout-inspector/proto:layout_inspector_cc_proto",
        "@skia_repo//:libskia",
    ],
)

flat_archive(
    name = "skiaparser",
    ext = "zip",
    deps = {
        "version-map.xml": "skiaparser",
        ":skia-grpc-server": "skiaparser",
        "source.properties": "skiaparser",
    },
)

cc_library(
    name = "agent_command",
    srcs = [
        "src/main/native/agent/commands/layoutinspector_agent_command.cc",
    ],
    hdrs = [
        "src/main/native/agent/commands/layoutinspector_agent_command.h",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/dynamic-layout-inspector/src/main/native/agent",
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
        "-Itools/base/transport/native",
        "-I$(GENDIR)/tools/base/transport",
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
        "src/main/native/agent/component_tree_java_jni.cc",
        "src/main/native/agent/compose_tree_java_jni.cc",
        "src/main/native/agent/layoutinspector_java_jni.cc",
        "src/main/native/agent/properties_java_jni.cc",
        "src/main/native/agent/resource_configuration_java_jni.cc",
    ],
    copts = ANDROID_COPTS + [
        # TODO: Remove dependency when agent.h no longer includes
        # memory_component.h.
        "-Itools/base/profiler/native/agent",
        "-Itools/base/transport/native",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/transport/native/agent",
        "//tools/base/transport/native/agent:jni_wrappers",
        "//tools/base/transport/native/utils",
        "//tools/base/transport/proto:cc_proto",
    ],
    alwayslink = 1,
)

cc_library(
    name = "native_test_support",
    srcs = [
        "src/test/native/testing/component_tree_test_java_jni.cc",
        "src/test/native/testing/properties_test_java_jni.cc",
    ],
    copts = ANDROID_COPTS + [
        "-Itools/base/profiler/native/agent",
        "-Itools/base/transport/native",
        "-I$(GENDIR)/tools/base/transport",
    ],
    linkstatic = 1,
    tags = ["no_windows"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/jdk:jni_headers",
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
        ":jni",
        ":native_test_support",
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
        ":jni",
        ":native_test_support",
    ],
)
