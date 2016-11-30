load("//tools/base/bazel:maven.bzl", "maven_repo")

java_library(
    name = "integration-test",
    srcs = glob([
        "src/main/java/**/*.java",
    ]) + [
        "//tools/base/build-system/integration-test/src/main/proto:proto_srcs",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle",
        "//tools/base/build-system/gradle-core",
        "//tools/base/build-system/gradle-experimental",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/instant-run:instant-run-client",
        "//tools/base/instant-run:instant-run-common",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testing-infrastructure/device-pool/device-provider",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.apis_google-api-services-storage",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.google.protobuf_protobuf-java-util",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:commons-io_commons-io",  # TODO: remove?
        "//tools/base/third_party:commons-lang_commons-lang",  # TODO: remove?
        "//tools/base/third_party:org.jacoco_org.jacoco.agent",
        "//tools/base/third_party:org.jacoco_org.jacoco.core",
        "//tools/base/third_party:org.jacoco_org.jacoco.report",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)

# TODO: Compile Groovy test as well (or just move all of them to Java).
java_test(
    name = "tests",
    srcs = glob(["src/test/java/**"]),
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
    ],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    tags = ["manual"],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":integration-test",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-core",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/instant-run:instant-run-client",
        "//tools/base/repository:tools.repository",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.google.protobuf_protobuf-java-util",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:commons-io_commons-io",  # TODO: remove?
        "//tools/base/third_party:org.codehaus.groovy_groovy-all",
        "//tools/base/third_party:org.mockito_mockito-all",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)

# Maven repo with all the dependencies required by test projects.
maven_repo(
    name = "prebuilts_repo",
    artifacts = [
        "//prebuilts/tools/common/m2/repository/org/jacoco/org.jacoco.agent/0.7.5.201505241946:jar",
    ],
)
