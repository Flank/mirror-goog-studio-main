load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "java_jarjar")
load("//tools/base/bazel:proto.bzl", "PROTOC_VERSION")
load("@bazel_skylib//lib:selects.bzl", "selects")
load("//tools/base/bazel:maven/artifacts.bzl", "ARTIFACTS", "DATA", "REMOTE_REPOS")

exports_files(["jarjar_rules.txt"])

java_library(
    name = "utils",
    srcs = glob(["src/com/android/tools/utils/*.java"]),
    visibility = ["//tools/base/bazel/sdk:__pkg__"],
    deps = ["@bazel_tools//src/main/protobuf:worker_protocol_java_proto"],
)

config_setting(
    name = "agent_coverage",
    values = {
        "define": "agent_coverage=true",
    },
    visibility = ["//visibility:public"],
)

java_binary(
    name = "unzipper",
    main_class = "com.android.tools.utils.Unzipper",
    visibility = ["//visibility:public"],
    runtime_deps = [":utils"],
)

java_binary(
    name = "kotlinc",
    srcs = ["src/com/android/tools/kotlin/KotlinCompiler.java"],
    main_class = "com.android.tools.kotlin.KotlinCompiler",
    visibility = ["//visibility:public"],
    deps = [
        ":utils",
        "//prebuilts/tools/common/m2:kotlin-compiler",
    ],
)

java_binary(
    name = "formc",
    srcs = ["src/com/android/tools/form/FormCompiler.java"],
    main_class = "com.android.tools.form.FormCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = ["@maven//:org.jetbrains.kotlin.kotlin-stdlib"],
    deps = [
        ":utils",
        "//prebuilts/tools/common/bazel/formc-deps",
    ],
)

java_library(
    name = "bazel_generator",
    srcs = glob([
        "src/com/android/tools/bazel/**",
        "src/com/intellij/util/graph/*",
    ]),
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
    ],
)

java_import(
    name = "langtools",
    jars = ["//prebuilts/studio/jdk:langtools"],
    visibility = ["//visibility:public"],
)

java_binary(
    name = "iml_to_build",
    srcs = ["src/com/android/tools/binaries/ImlToBazel.java"],
    main_class = "com.android.tools.binaries.ImlToBazel",
    visibility = ["//visibility:public"],
    deps = [
        ":bazel_generator",
        ":utils",
    ],
)

java_binary(
    name = "zip_merger",
    srcs = ["src/com/android/tools/binaries/ZipMerger.java"],
    main_class = "com.android.tools.binaries.ZipMerger",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
    ],
)

java_binary(
    name = "modify_jar_manifest",
    srcs = ["src/com/android/tools/binaries/ModifyJarManifest.java"],
    main_class = "com.android.tools.binaries.ModifyJarManifest",
    visibility = ["//visibility:public"],
)

java_test(
    name = "modify_jar_manifest_test",
    srcs = ["testSrc/com/android/tools/binaries/ModifyJarManifestTest.java"],
    test_class = "com.android.tools.binaries.ModifyJarManifestTest",
    deps = [
        ":modify_jar_manifest",
        "@maven//:junit.junit",
    ],
)

coverage_java_test(
    name = "zip_merger_tests",
    srcs = ["testSrc/com/android/tools/binaries/ZipMergerTest.java"],
    test_class = "com.android.tools.binaries.ZipMergerTest",
    deps = [
        ":zip_merger",
        "//tools/base/zipflinger",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)

# Verify that the iml_to_build tool does not need to be run.
java_test(
    name = "iml_to_build_consistency_test",
    srcs = ["testSrc/com/android/tools/binaries/ImlToBazelConsistencyTest.java"],
    # The iml_to_build tool depends on the artifact jars being present.
    data = ["//tools/adt/idea/android:artifacts"],
    # This test requires the tools/idea IML project and all corresponding
    # Bazel BUILD files as input. Rather than trying to model that, this
    # test is just run locally, without sandbox, and with caching disabled
    # (see tags).
    # Note that due to https://github.com/bazelbuild/bazel/issues/6038 the tag
    # isn't effective when run with --config=remote, hence the workaround in
    # test_studio.sh
    local = True,
    tags = [
        "no-cache",  # As this test doesn't model all of its inputs.
        "no_test_windows",
    ],
    test_class = "com.android.tools.binaries.ImlToBazelConsistencyTest",
    deps = [
        ":bazel_generator",
        ":iml_to_build",
        ":utils",
        "@maven//:junit.junit",
    ],
)

java_binary(
    name = "bin2c",
    srcs = ["src/com/android/tools/binaries/Bin2C.java"],
    main_class = "com.android.tools.binaries.Bin2C",
    visibility = ["//visibility:public"],
    deps = ["@maven//:com.google.guava.guava"],
)

java_binary(
    name = "properties_merger",
    main_class = "com.android.tools.utils.PropertiesMerger",
    visibility = ["//visibility:public"],
    runtime_deps = [":utils"],
)

java_library(
    name = "maven",
    srcs = glob(["src/com/android/tools/maven/**/*.java"]),
    deps = [
        "@maven//:com.google.code.findbugs.jsr305",
        "@maven//:com.google.guava.guava",
        "@maven//:org.apache.maven.maven-aether-provider",
        "@maven//:org.eclipse.aether.aether-connector-basic",
        "@maven//:org.eclipse.aether.aether-transport-http",
    ],
)

java_binary(
    name = "matryoshka",
    srcs = ["src/com/android/tools/binaries/Matryoshka.java"],
    main_class = "com.android.tools.binaries.Matryoshka",
    visibility = ["//visibility:public"],
)

exports_files(["maven/android.pom"])

java_binary(
    name = "pom_generator",
    srcs = ["src/com/android/tools/binaries/PomGenerator.java"],
    jvm_flags = ["-Xmx1g"],
    main_class = "com.android.tools.binaries.PomGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "@maven//:org.slf4j.slf4j-simple",
    ],
    deps = [
        ":maven",
        "@maven//:com.google.guava.guava",
        "@maven//:org.apache.maven.maven-aether-provider",
    ],
)

coverage_java_test(
    name = "pom_generator_test",
    srcs = ["testSrc/com/android/tools/binaries/PomGeneratorTest.java"],
    data = ["//tools/base/bazel/test:pom_generator"],
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.binaries.PomGeneratorTest",
    deps = [
        ":maven",
        ":pom_generator",
        "@maven//:junit.junit",
    ],
)

java_library(
    name = "repo_builder_lib",
    srcs = ["src/com/android/tools/binaries/RepoBuilder.java"],
    deps = [
        ":utils",
        "@maven//:org.apache.maven.maven-model-builder",
    ],
)

java_binary(
    name = "repo_builder",
    main_class = "com.android.tools.binaries.RepoBuilder",
    visibility = ["//visibility:public"],
    runtime_deps = [":repo_builder_lib"],
)

java_test(
    name = "repo_builder_test",
    srcs = ["testSrc/com/android/tools/binaries/RepoBuilderTest.java"],
    test_class = "com.android.tools.binaries.RepoBuilderTest",
    deps = [
        ":repo_builder_lib",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)

java_binary(
    name = "jarjar",
    main_class = "com.tonicsystems.jarjar.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2:jarjar_deps",
    ],
)

py_binary(
    name = "rename_jar_services",
    srcs = ["rename_jar_services.py"],
    visibility = ["//visibility:public"],
)

py_test(
    name = "rename_jar_services_test",
    srcs = ["rename_jar_services_test.py"],
    tags = [
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [":rename_jar_services"],
)

java_binary(
    name = "gradlew",
    main_class = "com.android.tools.gradle.GradleW",
    visibility = ["//visibility:public"],
    runtime_deps = [":gradle"],
)

java_binary(
    name = "apply_diff",
    srcs = ["src/com/android/tools/binaries/ApplyDiff.java"],
    main_class = "com.android.tools.binaries.ApplyDiff",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/testutils:tools.testutils",
    ],
)

java_library(
    name = "gradle",
    srcs = glob(["src/com/android/tools/gradle/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":utils",
        "//tools/base/annotations",
        "//tools/base/build-system:gradle-tooling-api",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "gradle_benchmarks",
    testonly = 1,
    srcs = glob(["testSrc/com/android/tools/gradle/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/base/annotations",
        "//tools/base/bazel:gradle",
        "//tools/base/common:tools.common",
        "//tools/base/perf-logger:studio.perf-logger_testlib",
        "//tools/base/testutils:tools.testutils",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

py_binary(
    name = "status_reader",
    srcs = ["status_reader.py"],
    visibility = ["//visibility:public"],
)

py_test(
    name = "status_reader_test",
    srcs = ["status_reader_test.py"],
    tags = [
        "no_mac",
        "no_windows",
    ],
    visibility = ["//visibility:public"],
    deps = [":status_reader"],
)

java_jarjar(
    name = "studio-proto",
    srcs = [
        "//prebuilts/tools/common/m2:com.google.protobuf.protobuf-java-util." + PROTOC_VERSION + "",
        "//prebuilts/tools/common/m2:com.google.protobuf.protobuf-java." + PROTOC_VERSION + "",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

java_jarjar(
    name = "studio-grpc",
    srcs = [
        "@maven//:io.grpc.grpc-api",
        "@maven//:io.grpc.grpc-auth",
        "@maven//:io.grpc.grpc-context",
        "@maven//:io.grpc.grpc-core",
        "@maven//:io.grpc.grpc-netty",
        "@maven//:io.grpc.grpc-netty-shaded",
        "@maven//:io.grpc.grpc-okhttp",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-protobuf-lite",
        "@maven//:io.grpc.grpc-stub",
        "@maven//:io.netty.netty-buffer",
        "@maven//:io.netty.netty-codec",
        "@maven//:io.netty.netty-codec-http",
        "@maven//:io.netty.netty-codec-http2",
        "@maven//:io.netty.netty-codec-socks",
        "@maven//:io.netty.netty-common",
        "@maven//:io.netty.netty-handler",
        "@maven//:io.netty.netty-handler-proxy",
        "@maven//:io.netty.netty-resolver",
        "@maven//:io.netty.netty-transport",
        "@maven//:io.opencensus.opencensus-api",
        "@maven//:io.opencensus.opencensus-contrib-grpc-metrics",
        "@maven//:io.perfmark.perfmark-api",
    ],
    rename_services = True,
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

config_setting(
    name = "host_linux",
    values = {"host_cpu": "linux"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "host_darwin",
    values = {"host_cpu": "darwin"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "host_windows",
    values = {"host_cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "darwin",
    values = {"cpu": "darwin"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "windows",
    values = {"cpu": "x64_windows"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "android_cpu_x86",
    values = {"cpu": "x86"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "android_cpu_x86_64",
    values = {"cpu": "x86_64"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "android_cpu_arm",
    values = {"cpu": "armeabi-v7a"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "android_cpu_arm_64",
    values = {"cpu": "arm64-v8a"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "release",
    values = {"define": "release=true"},
    visibility = ["//visibility:public"],
)

config_setting(
    name = "kotlin_no_use_ir",
    values = {"define": "kotlin_no_use_ir=true"},
    visibility = ["//visibility:public"],
)

selects.config_setting_group(
    name = "target_android",
    match_any = [
        "@//tools/base/bazel:android_cpu_x86",
        "@//tools/base/bazel:android_cpu_x86_64",
        "@//tools/base/bazel:android_cpu_arm",
        "@//tools/base/bazel:android_cpu_arm_64",
    ],
    visibility = ["//visibility:public"],
)

selects.config_setting_group(
    name = "host_un*x",
    match_any = [
        ":host_linux",
        ":host_darwin",
    ],
    visibility = ["//visibility:public"],
)

selects.config_setting_group(
    name = "target_android_host_un*x",
    match_all = [
        ":target_android",
        ":host_un*x",
    ],
    visibility = ["//visibility:public"],
)

coverage_java_test(
    name = "iml_to_build_tests",
    srcs = ["testSrc/com/android/tools/binaries/ImlToBazelTest.java"],
    data = ["//tools/base/bazel/test:iml_to_bazel"],
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.binaries.ImlToBazelTest",
    deps = [
        ":bazel_generator",
        ":iml_to_build",
        ":utils",
        "@maven//:junit.junit",
    ],
)

coverage_java_test(
    name = "bin2c_tests",
    srcs = ["testSrc/com/android/tools/binaries/Bin2CTest.java"],
    test_class = "com.android.tools.binaries.Bin2CTest",
    deps = [
        ":bin2c",
        "@maven//:com.google.guava.guava",
        "@maven//:junit.junit",
    ],
)

java_binary(
    name = "BazelLintWrapper",
    srcs = ["src/com/android/tools/binaries/BazelLintWrapper.java"],
    main_class = "com.android.tools.binaries.BazelLintWrapper",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/lint/cli",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "json",
    srcs = glob(["src/com/android/tools/json/**/*.java"]),
    deps = [
        "@maven//:com.fasterxml.jackson.core.jackson-annotations",
        "@maven//:com.fasterxml.jackson.core.jackson-core",
        "@maven//:com.fasterxml.jackson.core.jackson-databind",
    ],
)

java_binary(
    name = "local_maven_repository_generator",
    srcs = [
        "src/com/android/tools/binaries/LocalMavenRepositoryGenerator.java",
        "src/com/android/tools/repository_generator/BuildFileWriter.java",
        "src/com/android/tools/repository_generator/ResolutionResult.java",
    ],
    args = ARTIFACTS +
           ["+" + d for d in DATA] + [
        "--repo-path",
        "prebuilts/tools/common/m2/repository",
    ] + [item for pair in [(
        "--remote-repo",
        "\"" + k + "\"=\"" + v + "\"",
    ) for k, v in REMOTE_REPOS.items()] for item in pair] + [
        "-o",
        "tools/base/bazel/maven/BUILD.maven",
        "--fetch",
    ],
    main_class = "com.android.tools.binaries.LocalMavenRepositoryGenerator",
    visibility = ["//visibility:public"],
    deps = [
        ":json",
        ":maven",
        ":utils",
        "@maven//:com.google.code.findbugs.jsr305",
        "@maven//:com.google.guava.guava",
        "@maven//:org.apache.maven.maven-aether-provider",
        "@maven//:org.eclipse.aether.aether-connector-basic",
        "@maven//:org.eclipse.aether.aether-transport-file",
        "@maven//:org.eclipse.aether.aether-transport-http",
    ],
)

coverage_java_test(
    name = "local_maven_repository_generator_tests",
    srcs = ["testSrc/com/android/tools/binaries/LocalMavenRepositoryGeneratorTest.java"],
    data = ["//tools/base/bazel/test:local_maven_repository_generator"],
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.binaries.LocalMavenRepositoryGeneratorTest",
    deps = [
        ":json",
        ":local_maven_repository_generator",
        ":maven",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
        "@maven//:org.apache.maven.maven-aether-provider",
        "@maven//:org.eclipse.aether.aether-transport-file",
    ],
)

coverage_java_test(
    name = "maven_consistency_test",
    srcs = ["testSrc/com/android/tools/binaries/MavenConsistencyTest.java"],
    data = [
        "maven/BUILD.maven",
        "//prebuilts/tools/common/m2:all",
        "//tools/base/bazel/test:local_maven_repository_generator",
    ],
    env = {
        "MAVEN_ARTIFACTS": ";".join(ARTIFACTS),
        "MAVEN_DATA": ";".join(DATA),
        "MAVEN_REPO_PATH": "prebuilts/tools/common/m2/repository",
        "MAVEN_REMOTE_REPO_KEYS": ";".join(REMOTE_REPOS.keys()),
        "MAVEN_REMOTE_REPO_VALUES": ";".join(REMOTE_REPOS.values()),
        "MAVEN_OUTPUT_FILE": "tools/base/bazel/maven/BUILD.maven",
    },
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.binaries.MavenConsistencyTest",
    deps = [
        ":json",
        ":local_maven_repository_generator",
        ":maven",
        "//tools/base/testutils:tools.testutils",
        "@maven//:junit.junit",
        "@maven//:org.apache.maven.maven-aether-provider",
        "@maven//:org.eclipse.aether.aether-transport-file",
    ],
)
