load("//tools/base/bazel:maven.bzl", "maven_repo")

java_library(
    name = "integration-test",
    srcs = glob([
        "src/main/java/**/*.java",
    ]) + [
        "//tools/base/build-system/integration-test/src/main/proto:proto_srcs",
    ],
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
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
        "//tools/base/third_party:org.smali_dexlib2",
    ],
)

# TODO: Compile Groovy test as well (or just move all of them to Java).
java_library(
    name = "java_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = [
            "src/test/java/**/CMake*",
            "src/test/java/**/CheckAll*",
            "src/test/java/**/Cmake*",
            "src/test/java/**/ExternalNative*",
            "src/test/java/**/Ndk*",
            "src/test/java/**/ndk/**",  # TODO: check in the NDK
            "src/test/java/**/performance/**",
        ],
    ),
    resources = glob(["src/test/resources/**"]),
    deps = [
        ":integration-test",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-api",
        "//tools/base/build-system:tools.builder-test-api",
        "//tools/base/build-system/builder",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/gradle-core",
        "//tools/base/build-system/gradle-experimental",
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

java_test(
    name = "tests",
    size = "enormous",
    data = [
        ":prebuilts_repo_repo.zip",
        ":test-projects",
        "//prebuilts/studio/sdk:build-tools/25.0.0",
        "//prebuilts/studio/sdk:espresso_latest",
        "//prebuilts/studio/sdk:gms_latest",
        "//prebuilts/studio/sdk:multidex",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/android-19",
        "//prebuilts/studio/sdk:platforms/android-21",
        "//prebuilts/studio/sdk:platforms/android-23",
        "//prebuilts/studio/sdk:platforms/android-24",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:support_latest",
        "//prebuilts/studio/sdk:test-runner_latest",
        "//tools/base/bazel:offline_repo_repo.zip",
        "//tools/base/build-system:gradle-distrib",
    ],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    shard_count = 16,
    tags = ["manual"],
    test_class = "com.android.build.gradle.integration.BazelIntegrationTestsSuite",
    runtime_deps = [
        # Need to put this on the classpath before TestRunner_deploy.jar which contains
        # old JUnit classes. See https://github.com/bazelbuild/bazel/issues/2146.
        "//tools/base/third_party:junit_junit",
        ":java_tests",
    ],
)

filegroup(
    name = "test-projects",
    srcs = glob(["test-projects/**"]),
)

# Maven repo with all the dependencies required by test projects.
maven_repo(
    name = "prebuilts_repo",
    artifacts = [
        "//prebuilts/tools/common/m2/repository/com/google/code/findbugs/jsr305/1.3.9:jar",
        "//prebuilts/tools/common/m2/repository/com/google/dagger/dagger-compiler/2.6:jar",
        "//prebuilts/tools/common/m2/repository/com/google/dagger/dagger-producers/2.6:jar",
        "//prebuilts/tools/common/m2/repository/com/google/dagger/dagger/2.6:jar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/18.0:jar",
        "//prebuilts/tools/common/m2/repository/com/googlecode/jarjar/jarjar/1.3:jar",
        "//prebuilts/tools/common/m2/repository/com/jakewharton/butterknife/7.0.1:jar",
        "//prebuilts/tools/common/m2/repository/com/neenbedankt/gradle/plugins/android-apt/1.8:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/dagger/dagger-compiler/1.2.2:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/dagger/dagger/1.2.2:jar",
        "//prebuilts/tools/common/m2/repository/commons-codec/commons-codec/1.4:jar",
        "//prebuilts/tools/common/m2/repository/javax/annotation/jsr250-api/1.0:jar",
        "//prebuilts/tools/common/m2/repository/javax/inject/javax.inject/1:jar",
        "//prebuilts/tools/common/m2/repository/javax/inject/javax.inject/1:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpclient/4.1.1:jar",
        "//prebuilts/tools/common/m2/repository/org/bouncycastle/bcprov-jdk16/1.46:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-android-aar/1.2.3:aar",
        "//tools/base/testing-infrastructure/device-pool/device-provider",  # TODO: neverlink
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.jacoco_org.jacoco.agent",
    ],
)
