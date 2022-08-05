load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/common:version.bzl", "BASE_VERSION")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:proto.bzl", "android_java_proto_library", "java_proto_library")
load("//tools/base/bazel/avd:avd.bzl", "avd")

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.ddmlib",
    srcs = ["src/main/java"],
    iml_files = ["android.sdktools.ddmlib.iml"],
    jvm_target = "8",
    lint_baseline = "lint_baseline.xml",
    test_data = ["//prebuilts/studio/sdk:platform-tools"],
    test_resources = ["src/test/resources"],
    test_srcs = ["src/test/java"],
    test_tags = ["very_flaky"],  # b/192559588, b/240467082
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/base/common:studio.android.sdktools.common",
        "//tools/adt/idea/.idea/libraries:kxml2",
    ],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/base/common:studio.android.sdktools.common[module]",
        "//tools/adt/idea/.idea/libraries:kxml2",
        "//tools/adt/idea/.idea/libraries:easymock[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/fakeadbserver:studio.android.sdktools.fakeadbserver[module, test]",
        "//tools/adt/idea/.idea/libraries:libam-instrumentation-data-proto",
        "//tools/adt/idea/.idea/libraries:libapp-processes-proto",
    ],
)

# Compile ddmlib without any add-ons to ensure it builds on it's own. Once the AGP - ddmlib
# dependency is reworked, we can move the code around to properly represent the separation.
kotlin_library(
    name = "ddmlib-base",
    srcs = glob([
        "src/main/java/com/android/ddmlib/**/*.java",
        "src/main/java/com/android/ddmlib/**/*.kt",
    ]),
    resource_strip_prefix = "tools/base/ddmlib",
    visibility = ["//visibility:public"],
    deps = [
        ":am-instrumentation-data-proto",
        ":app-processes-proto",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:net.sf.kxml.kxml2",
    ],
)

# Compile ddmlib tests as a stand alone library so that test classes (e.g. FakeAdbRule)
# can be re-used from other "kotlin_test" targets.
kotlin_library(
    name = "test-utils",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    data = ["//prebuilts/studio/sdk:platform-tools"],
    resources = glob(["src/test/resources/**"]),
    visibility = ["//visibility:public"],
    deps = [
        ":tools.ddmlib",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/fakeadbserver:tools.fakeadbserver",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.easymock.easymock",
        "@maven//:org.mockito.mockito-core",
    ],
)

# Incremental library in ddmlib is standalone
java_library(
    name = "incfs",
    srcs = glob(["src/main/java/com/android/incfs/**/*.java"]),
    resource_strip_prefix = "tools/base/ddmlib",
    visibility = ["//visibility:public"],
    deps = [
        ":ddmlib-base",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "tools.ddmlib.resources",
    resource_strip_prefix = "tools/base/ddmlib/src/main/proto/",
    resources = [
        "src/main/proto/app_processes.proto",
        "src/main/proto/instrumentation-data.proto",
    ],
)

# For now instead of combining ddmlib-base and its add-ons, we recompile all together
# until we sort out the AGP - ddmlib dependency. This matches what is done in build.gradle.
# Note that this target is released via go/ab studio_linux, for its usage in the platform.
# Kotlin is not enabled in ddmlib module but here we use kotlin_library rule
# for bundled_deps support.
maven_library(
    name = "tools.ddmlib",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        ":am-instrumentation-data-proto",
        ":app-processes-proto",
        ":tools.ddmlib.resources",
    ],
    coordinates = "com.android.tools.ddms:ddmlib:" + BASE_VERSION,
    description = "Library providing APIs to talk to Android devices",
    module_name = "ddmlib",
    notice = "NOTICE",
    pom_name = "Android Tools ddmlib",
    resource_strip_prefix = "tools/base/ddmlib",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/common:tools.common",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:net.sf.kxml.kxml2",
        "@maven//:org.jetbrains.annotations",
    ],
)

kotlin_test(
    name = "tools.tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    data = ["//prebuilts/studio/sdk:platform-tools"],
    jvm_flags = ["-Dtest.suite.jar=tools.tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":tools.ddmlib",
        "//tools/base/annotations",
        "//tools/base/common:tools.common",
        "//tools/base/fakeadbserver:tools.fakeadbserver",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
        "@maven//:org.easymock.easymock",
        "@maven//:org.mockito.mockito-core",
    ],
)

avd(name = "avd")

java_proto_library(
    name = "am-instrumentation-data-proto",
    srcs = glob(["src/main/proto/instrumentation-data.proto"]),
    visibility = ["//visibility:public"],
)

java_proto_library(
    name = "app-processes-proto",
    srcs = glob(["src/main/proto/app_processes.proto"]),
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "studio.android.sdktools.ddmlib.integration.tests",
    iml_files = ["android.sdktools.ddmlib.integration.tests.iml"],
    tags = [
        "no_mac",
        "no_windows",
    ],
    test_data = [
        ":avd",
        "//prebuilts/studio/sdk:platform-tools",
    ],
    test_srcs = ["src/integration/test/java"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.android.sdktools.common[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/base/bazel/avd:studio.android.sdktools.base.bazel.avd[module, test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module, test]",
        "//prebuilts/studio/intellij-sdk:studio-sdk[test]",
    ],
)
