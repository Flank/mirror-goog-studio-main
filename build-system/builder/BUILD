load("//tools/base/bazel:bazel.bzl", "fileset")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")

fileset(
    name = "resources",
    srcs = ["//tools/buildSrc/base:version.properties"] + glob(["src/main/resources/**"]),
    mappings = {
        "//tools/buildSrc/base:": "com/android/builder/",
        "src/main/resources/": "",
    },
)

maven_java_library(
    name = "builder",
    srcs = glob(["src/main/java/**/*.java"]) + [
        "//tools/base/apkzlib:sources",
        "//tools/base/build-system/profile:sources",  # TODO: Implement this properly.
    ],
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/builder",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/jack/jack-api",
        "//tools/base/jack/jill-api",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.squareup_javawriter",
        "//tools/base/third_party:commons-codec_commons-codec",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
    ],
)

maven_pom(
    name = "pom",
    artifact = "builder",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
)

java_test(
    name = "tests",
    srcs = glob(["src/test/java/**"]),
    data = [
        "//prebuilts/studio/sdk:build-tools/25.0.0",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":builder",
        "//tools/base/annotations",
        "//tools/base/apkzlib",
        "//tools/base/build-system/builder-model",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.mockito_mockito-all",
    ],
)
