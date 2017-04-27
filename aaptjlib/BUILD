# Bazel (https://bazel.io/) BUILD file for aaptjlib library.
load("//tools/base/bazel:bazel.bzl", "fileset")

exports_files(glob(["src/main/resources/**"]))

licenses(["notice"])  # Apache License 2.0

load("//tools/base/bazel:utils.bzl", "srcjar")
load("//tools/base/bazel:bazel.bzl", "fileset")

srcjar(
    name = "srcjar",
    java_library = ":aaptjlib_src",
    visibility = ["//tools/base/build-system/builder:__pkg__"],
)

fileset(
    name = "resources",
    srcs = [
        # TODO: figure out how to include all resources from aaptjlib automatically.
        "//prebuilts/tools/common/aapt/jni:linux64/libaapt2_jni.so",
        "//prebuilts/tools/common/aapt/jni:linux64/libc++.so",
        "//prebuilts/tools/common/aapt/jni:mac64/libaapt2_jni.dylib",
        "//prebuilts/tools/common/aapt/jni:mac64/libc++.dylib",
        "//prebuilts/tools/common/aapt/jni:win32/libaapt2_jni.dll",
        "//prebuilts/tools/common/aapt/jni:win64/libaapt2_jni.dll",
        "//prebuilts/tools/common/aapt/jni:win32/libwinpthread-1.dll",
        "//prebuilts/tools/common/aapt/jni:win64/libwinpthread-1.dll",
    ],
    mappings = {
        # This is an (ab)use of the bazel heuristic: see
        # https://bazel.build/versions/master/docs/be/java.html#java_binary.resources
        "//prebuilts/tools/common/aapt/jni:": "src/main/resources/",
    },
)

DEPS = [
    "//tools/base/annotations",
    "//tools/base/third_party:com.google.auto.value_auto-value",
    "//tools/base/third_party:com.google.code.findbugs_jsr305",
    "//tools/base/third_party:com.google.guava_guava",
]

java_library(
    name = "aaptjlib",
    srcs = glob(["src/main/java/**/*.java"]),
    plugins = [
        ":auto-value-plugin",
    ],
    resources = [":resources"],
    deps = DEPS,
)

# srcjar includes the generated sources, which will get regenerated in builder
# So have a fake library just to collect the real sources.
java_library(
    name = "aaptjlib_src",
    srcs = glob(["src/main/java/**/*.java"]),
    tags = ["manual"],
    deps = DEPS,
)

java_test(
    name = "aaptjlib_tests",
    size = "small",
    srcs = glob([
        "src/test/java/**/*.java",
    ]),
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    runtime_deps = ["//tools/base/testutils:tools.testutils"],
    deps = [
        ":aaptjlib",
        "//tools/base/annotations",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)

java_plugin(
    name = "auto-value-plugin",
    processor_class = "com.google.auto.value.processor.AutoValueProcessor",
    deps = [
        "//tools/base/third_party:com.google.auto.value_auto-value",
    ],
)
