load("//tools/base/bazel:maven.bzl", "maven_repo")
load("//tools/base/bazel:groovy.bzl", "groovy_jar")

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

TEST_DEPS = [
    ":integration-test",
    "//tools/apksig",
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
]

TEST_DATA = [
    ":prebuilts_repo_repo.zip",
    ":test-projects",
    "@AntennaPod//:all_files",
    "//prebuilts/studio/sdk:add-ons/addon-google_apis-google-latest",
    "//prebuilts/studio/sdk:build-tools/24.0.3",
    "//prebuilts/studio/sdk:build-tools/minimum",
    "//prebuilts/studio/sdk:cmake",
    "//prebuilts/studio/sdk:databinding_latest",
    "//prebuilts/studio/sdk:espresso_latest",
    "//prebuilts/studio/sdk:gms_latest",
    "//prebuilts/studio/sdk:multidex",
    "//prebuilts/studio/sdk:ndk-bundle",
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
]

# TODO: Compile Groovy test as well (or just move all of them to Java).
java_library(
    name = "java_tests",
    srcs = glob(
        ["src/test/java/**/*.java"],
        exclude = [
            "src/test/java/**/CheckAll*",
            "src/test/java/**/performance/**",
        ],
    ),
    resources = glob(["src/test/resources/**"]),
    deps = TEST_DEPS,
)

groovy_jar(
    name = "groovy_tests",
    srcs = glob(
        ["src/test/groovy/**/*.groovy"],
    ),
    deps = TEST_DEPS,
)

java_test(
    name = "tests",
    size = "enormous",
    data = TEST_DATA,
    jvm_flags = [
        "-Dtest.suite.jar=tests.jar",
        "-Dfile.encoding=UTF-8",
        "-Dsun.jnu.encoding=UTF-8",
        "-Dmaven.repo.local=/tmp/localMavenRepo",  # For gradle publishing, writing to ~/.m2
        "-Dtest.excludeCategories=com.android.build.gradle.integration.common.category.DeviceTests,com.android.build.gradle.integration.common.category.OnlineTests",
    ],
    shard_count = 8,
    tags = ["exclusive"],
    test_class = "com.android.build.gradle.integration.BazelIntegrationTestsSuite",
    runtime_deps = [
        # Need to put this on the classpath before TestRunner_deploy.jar which contains
        # old JUnit classes. See https://github.com/bazelbuild/bazel/issues/2146.
        "//tools/base/third_party:junit_junit",
        ":java_tests",
        ":libgroovy_tests.jar",
    ],
)

# Test that builds and lints all test projects, without any additional assertions.
java_test(
    name = "automatic-tests",
    srcs = glob(["src/test/java/**/CheckAll.java"]),
    data = TEST_DATA,
    jvm_flags = [
        "-Dtest.suite.jar=automatic-tests.jar",
        "-Djunit.parallel.threads=8",
    ],
    tags = ["exclusive"],
    test_class = "com.android.build.gradle.integration.BazelIntegrationTestsSuite",
    runtime_deps = [
        # Need to put this on the classpath before TestRunner_deploy.jar which contains
        # old JUnit classes. See https://github.com/bazelbuild/bazel/issues/2146.
        "//tools/base/third_party:junit_junit",
    ],
    deps = TEST_DEPS,
)

filegroup(
    name = "test-projects",
    srcs = glob(["test-projects/**"]),
)

# Maven repo with all the dependencies required by test projects.
maven_repo(
    name = "prebuilts_repo",
    artifacts = [
        "//prebuilts/tools/common/m2/repository/com/android/tools/build/transform-api/1.5.0:jar",
        "//prebuilts/tools/common/m2/repository/com/artemzin/rxjava/proguard-rules/1.1.0.0:aar",
        "//prebuilts/tools/common/m2/repository/com/githang/viewpagerindicator/2.5:aar",
        "//prebuilts/tools/common/m2/repository/com/github/ben-manes/gradle-versions-plugin/0.11.3:jar",
        "//prebuilts/tools/common/m2/repository/com/github/bumptech/glide/glide/3.6.1:jar",
        "//prebuilts/tools/common/m2/repository/com/github/bumptech/glide/okhttp-integration/1.3.1:jar",
        "//prebuilts/tools/common/m2/repository/com/github/dcendents/android-maven-gradle-plugin/1.4.1:jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/findbugs/jsr305/1.3.9:jar",
        "//prebuilts/tools/common/m2/repository/com/google/code/findbugs/jsr305/2.0.1:jar",  # TODO: single version.
        "//prebuilts/tools/common/m2/repository/com/google/dagger/dagger-compiler/2.6:jar",
        "//prebuilts/tools/common/m2/repository/com/google/dagger/dagger-producers/2.6:jar",
        "//prebuilts/tools/common/m2/repository/com/google/dagger/dagger/2.6:jar",
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/18.0:jar",
        "//prebuilts/tools/common/m2/repository/com/googlecode/jarjar/jarjar/1.3:jar",
        "//prebuilts/tools/common/m2/repository/com/jakewharton/butterknife/7.0.1:jar",
        "//prebuilts/tools/common/m2/repository/com/jayway/android/robotium/robotium-solo/5.5.3:jar",
        "//prebuilts/tools/common/m2/repository/com/joanzapata/iconify/android-iconify-fontawesome/2.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/joanzapata/iconify/android-iconify-material/2.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/joanzapata/iconify/android-iconify/2.1.1:aar",
        "//prebuilts/tools/common/m2/repository/com/neenbedankt/gradle/plugins/android-apt/1.8:jar",
        "//prebuilts/tools/common/m2/repository/com/nineoldandroids/library/2.4.0:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/dagger/dagger-compiler/1.2.2:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/dagger/dagger/1.2.2:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/javawriter/2.1.1:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp/okhttp-urlconnection/2.7.4:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okhttp/okhttp/2.7.4:jar",
        "//prebuilts/tools/common/m2/repository/com/squareup/okio/okio/1.6.0:jar",
        "//prebuilts/tools/common/m2/repository/com/thoughtworks/xstream/xstream/1.4.7:jar",
        "//prebuilts/tools/common/m2/repository/com/yqritc/recyclerview-flexibledivider/1.2.6:aar",
        "//prebuilts/tools/common/m2/repository/commons-codec/commons-codec/1.4:jar",
        "//prebuilts/tools/common/m2/repository/de/greenrobot/eventbus/2.4.0:jar",
        "//prebuilts/tools/common/m2/repository/io/reactivex/rxandroid/1.1.0:aar",
        "//prebuilts/tools/common/m2/repository/io/reactivex/rxjava/1.1.0:jar",
        "//prebuilts/tools/common/m2/repository/javax/annotation/javax.annotation-api/1.2:jar",
        "//prebuilts/tools/common/m2/repository/javax/annotation/jsr250-api/1.0:jar",
        "//prebuilts/tools/common/m2/repository/javax/inject/javax.inject/1:jar",
        "//prebuilts/tools/common/m2/repository/me/tatarka/gradle-retrolambda/3.2.4:jar",
        "//prebuilts/tools/common/m2/repository/me/tatarka/retrolambda/projectlombok/lombok.ast/0.2.3.a2:jar",
        "//prebuilts/tools/common/m2/repository/me/zhanghai/android/materialprogressbar/library/1.1.4:aar",
        "//prebuilts/tools/common/m2/repository/org/apache/commons/commons-lang3/3.4:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpclient/4.1.1:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/httpcomponents/httpcore/4.1:jar",
        "//prebuilts/tools/common/m2/repository/org/bouncycastle/bcprov-jdk16/1.46:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-integration/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/hamcrest/hamcrest-library/1.3:jar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-android-aar/1.2.2:aar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-android-aar/1.2.3:aar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-core/1.2.2:jar",
        "//prebuilts/tools/common/m2/repository/org/jdeferred/jdeferred-core/1.2.3:jar",
        "//prebuilts/tools/common/m2/repository/org/json/json/20141113:jar",
        "//prebuilts/tools/common/m2/repository/org/jsoup/jsoup/1.7.3:jar",
        "//prebuilts/tools/common/m2/repository/org/shredzone/flattr4j/flattr4j-core/2.12:jar",
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-api/1.7.2:jar",
        "//prebuilts/tools/common/m2/repository/xmlpull/xmlpull/1.1.3.1:jar",
        "//prebuilts/tools/common/m2/repository/xpp3/xpp3_min/1.1.4c:jar",
        "//prebuilts/tools/common/m2/repository/net/orfjackal/retrolambda/retrolambda/2.0.6:jar",
        "//tools/base/testing-infrastructure/device-pool/device-provider",  # TODO: neverlink
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.jacoco_org.jacoco.agent",
        "//tools/base/third_party:org.mockito_mockito-core",
    ],
)
