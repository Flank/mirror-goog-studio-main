load("//tools/base/bazel:android.bzl", "dex_library")
load("//tools/base/bazel:coverage.bzl", "coverage_java_library", "coverage_java_test")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

coverage_java_library(
    name = "interpreter_common",
    srcs = glob([
        "src/main/java/com/android/tools/deploy/interpreter/**/*.java",
    ]),
    deps = [
        ":asm4",
        "//tools/base/annotations",
    ],
)

# Generates asm4.jar. Must be run manually and output copied to lib/asm4.jar.
# $ tools/base/bazel/bazel build tools/base/deploy/agent/runtime:jarjar_asm4
# $ cp bazel-bin/tools/base/deploy/agent/runtime/jarjar_asm4.jar tools/base/deploy/agent/runtime/lib/deploy_asm4.jar
java_jarjar(
    name = "jarjar_asm4",
    srcs = [
        "lib/asm-all-9.0.jar",
    ],
    rules = "jarjar_asm4_rules.txt",
    visibility = ["//visibility:private"],
)

java_import(
    name = "asm4",
    jars = [
        "lib/deploy_asm4.jar",
    ],
)

coverage_java_library(
    name = "instrument_java",
    srcs = [
        "//tools/base/deploy/sites:Sites.java",
    ] + glob([
        "src/main/java/com/android/tools/deploy/instrument/**/*.java",
    ]),
    deps = [
        "//prebuilts/studio/sdk:platforms/latest_jar",
    ],
)

coverage_java_library(
    name = "liveedit_java",
    srcs = glob([
        "src/main/java/com/android/tools/deploy/liveedit/*.java",
        "src/main/java/com/android/tools/deploy/liveedit/backported/*.java",
    ]),
    deps = [
        ":asm4",
        ":instrument_java",
        ":interpreter_common",
        "//prebuilts/studio/sdk:platforms/latest_jar",
        "//tools/base/annotations",
    ],
)

coverage_java_library(
    name = "liveedit_lambdalib_java",
    srcs = glob([
        "src/main/java/com/android/tools/deploy/liveedit/superclasses/*.java",
    ]),
    deps = [
        ":liveedit_java",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
    ],
)

kotlin_library(
    name = "runtime_kotlin_test",
    srcs = glob([
        "src/test/**/*.kt",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)

java_library(
    name = "new_proxy_test_jar",
    srcs = ["src/test/java/com/android/tools/deploy/liveedit/proxy/new/ProxyTestClasses.java"],
    visibility = ["//visibility:public"],
)

coverage_java_test(
    name = "runtime_java_test",
    size = "small",
    srcs = glob(
        ["src/test/java/**/*.java"],
        ["src/test/java/com/android/tools/deploy/liveedit/proxy/new/ProxyTestClasses.java"],
    ),
    data = [
        ":new_proxy_test_jar",
        "//tools/base/deploy/agent/native/jni_dispatch:jni_dispatch_dso",
    ],
    jvm_flags = [
        "-Dtest.suite.jar=runtime_java_test.jar",
        "-Ddashboards.enabled=true",
        "-Djava.library.path=tools/base/deploy/agent/native/jni_dispatch",
        "-Dproxy.new.classes=$(location :new_proxy_test_jar)",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":asm4",
        ":instrument_java",
        ":interpreter_common",
        ":liveedit_java",
        ":liveedit_lambdalib_java",
        ":runtime_kotlin_test",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
        "@maven//:org.mockito.mockito-core",
    ],
)

dex_library(
    name = "runtime",
    dexer = "D8",
    flags = ["--min-api 26"],
    jars = [
        ":instrument_java",
        ":liveedit_java",
        ":interpreter_common",
        ":asm4",
    ],
    visibility = ["//visibility:public"],
)

# Contains all Live Edit classes that need to be loaded by the application class loader.
dex_library(
    name = "live_edit_app_jar",
    dexer = "D8",
    flags = ["--min-api 26"],
    jars = [
        ":liveedit_lambdalib_java",
    ],
)

# Extracts the Live Edit dex from the jar so it can be added to the dex path of the class loader.
genrule(
    name = "live_edit_dex",
    srcs = [":live_edit_app_jar"],
    outs = ["live_edit.dex"],
    cmd = "$(location //tools/base/bazel:unzipper) $(location :live_edit_app_jar) classes.dex:$(location :live_edit.dex)",
    tools = ["//tools/base/bazel:unzipper"],
    visibility = ["//tools/base/deploy:__subpackages__"],
)
