package(default_visibility = ["//visibility:public"])

load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:utils.bzl", "flat_archive")

kotlin_test(
    name = "skiaparser_tests",
    testonly = 1,
    srcs = glob([
        "src/test/com/android/tools/layoutinspector/**/*.kt",
        "src/test/com/android/tools/layoutinspector/**/*.java",
    ]),
    data = select({
        "//tools/base/bazel:windows": [":skiaparser-test.dll"],
        "//tools/base/bazel:darwin": [":libskiaparser-test.dylib"],
        "//conditions:default": [":libskiaparser-test.so"],
    }) + glob([
        "testData/**",
        "files/**",
    ]),
    jvm_flags = [
        "-Dtest.suite.jar=skiaparser_tests.jar",
        "-Djava.library.path=tools/base/dynamic-layout-inspector/skia",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        "//tools/base/dynamic-layout-inspector/common:studio.dynamic-layout-inspector.common",
        "//tools/base/dynamic-layout-inspector/skia/proto:layout_inspector_skia_java_proto",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-test",
    ],
)

cc_binary(
    name = "skia-grpc-server",
    copts = [
        "-DSK_USER_CONFIG_HEADER=\\\"StudioConfig.h\\\"",
        "-UFORTIFY_SOURCE",
        "-DATRACE_TAG=ATRACE_TAG_VIEW",
        "-DSKIA_IMPLEMENTATION=1",
        "-DSK_PRINT_CODEC_MESSAGES",
        "-D_FORTIFY_SOURCE=1",
    ] + select({
        "//tools/base/bazel:windows": [],
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
    deps = [":skia-lib"],
)

cc_library(
    name = "skia-lib",
    srcs = glob([
        "src/main/native/tree_building_canvas*.cc",
        "src/main/native/*.h",
    ]) + ["src/main/native/server.cc"],
    copts = [
        "-DSK_USER_CONFIG_HEADER=\\\"StudioConfig.h\\\"",
        "-UFORTIFY_SOURCE",
        "-DATRACE_TAG=ATRACE_TAG_VIEW",
        "-DSKIA_IMPLEMENTATION=1",
        "-DSK_PRINT_CODEC_MESSAGES",
        "-D_FORTIFY_SOURCE=1",
    ] + select({
        "//tools/base/bazel:windows": [],
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
    includes = ["src/main/native"],
    linkopts = select({
        "//tools/base/bazel:windows": ["Ws2_32.lib"],
        "//conditions:default": ["-ldl"],
    }),
    linkstatic = 1,
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/dynamic-layout-inspector/skia/proto:layout_inspector_skia_cc_proto",
        "@skia_repo//:libskia",
    ],
    alwayslink = 1,
)

flat_archive(
    name = "skiaparser",
    ext = "zip",
    deps = {
        "files/version-map.xml": "skiaparser",
        ":skia-grpc-server": "skiaparser",
        "files/source.properties": "skiaparser",
    },
)

cc_library(
    name = "skiaparser_test_support",
    srcs = ["src/test/native/testing/skia_parser_test_java_jni.cc"],
    copts = [
        "-DSK_USER_CONFIG_HEADER=\\\"StudioConfig.h\\\"",
    ],
    linkstatic = 1,
    deps = [
        ":skia-lib",
        "//prebuilts/studio/jdk:jni_headers",
        "//tools/base/dynamic-layout-inspector/skia/proto:layout_inspector_skia_cc_proto",
    ],
    alwayslink = 1,
)

cc_binary(
    name = "libskiaparser-test.dylib",
    linkshared = 1,
    tags = [
        "no_linux",
        "no_windows",
    ],
    deps = [
        ":skiaparser_test_support",
    ],
)

cc_binary(
    name = "libskiaparser-test.so",
    linkshared = 1,
    tags = [
        "no_mac",
        "no_windows",
    ],
    deps = [
        ":skiaparser_test_support",
    ],
)

cc_binary(
    name = "skiaparser-test.dll",
    linkshared = 1,
    tags = [
        "no_linux",
        "no_mac",
    ],
    deps = [
        ":skiaparser_test_support",
    ],
)
