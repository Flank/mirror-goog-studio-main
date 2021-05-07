load("//tools/base/bazel:coverage.bzl", "coverage_java_test")
load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "java_jarjar")
load("//tools/base/bazel:proto.bzl", "PROTOC_VERSION")
load("@bazel_skylib//lib:selects.bzl", "selects")

exports_files(["jarjar_rules.txt"])

java_library(
    name = "utils",
    srcs = glob(["src/com/android/tools/utils/*.java"]),
    visibility = ["//tools/base/bazel/sdk:__pkg__"],
    deps = ["@io_bazel_rules_kotlin//src/main/protobuf:worker_protocol_java_proto"],
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
    data = ["//prebuilts/tools/common/kotlin-plugin-ij:kotlin_plugin"],
    main_class = "com.android.tools.kotlin.KotlinCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-reflect",
        "//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib",
    ],
    deps = [
        ":utils",
        "//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-compiler",
    ],
)

java_binary(
    name = "formc",
    srcs = ["src/com/android/tools/form/FormCompiler.java"],
    main_class = "com.android.tools.form.FormCompiler",
    visibility = ["//visibility:public"],
    runtime_deps = ["//prebuilts/tools/common/kotlin-plugin-ij:Kotlin/kotlinc/lib/kotlin-stdlib"],
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
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/zipflinger",
    ],
)

coverage_java_test(
    name = "zip_merger_tests",
    srcs = ["testSrc/com/android/tools/binaries/ZipMergerTest.java"],
    test_class = "com.android.tools.binaries.ZipMergerTest",
    deps = [
        ":zip_merger",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
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
        "//tools/base/third_party:junit_junit",
    ],
)

java_binary(
    name = "bin2c",
    srcs = ["src/com/android/tools/binaries/Bin2C.java"],
    main_class = "com.android.tools.binaries.Bin2C",
    visibility = ["//visibility:public"],
    deps = ["//tools/base/third_party:com.google.guava_guava"],
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
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
        "//tools/base/third_party:org.eclipse.aether_aether-connector-basic",
        "//tools/base/third_party:org.eclipse.aether_aether-transport-http",
    ],
)

java_binary(
    name = "matryoshka",
    srcs = ["src/com/android/tools/binaries/Matryoshka.java"],
    main_class = "com.android.tools.binaries.Matryoshka",
    visibility = ["//visibility:public"],
)

java_binary(
    name = "pom_generator",
    srcs = ["src/com/android/tools/binaries/PomGenerator.java"],
    jvm_flags = ["-Xmx1g"],
    main_class = "com.android.tools.binaries.PomGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

java_binary(
    name = "repo_builder",
    srcs = ["src/com/android/tools/binaries/RepoBuilder.java"],
    main_class = "com.android.tools.binaries.RepoBuilder",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        ":utils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

# See README.md
java_binary(
    name = "java_import_generator",
    srcs = ["src/com/android/tools/binaries/JavaImportGenerator.java"],
    main_class = "com.android.tools.binaries.JavaImportGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        ":utils",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

# See README.md
java_binary(
    name = "third_party_build_generator",
    srcs = [
        "src/com/android/tools/binaries/JavaImportGenerator.java",
        "src/com/android/tools/binaries/ThirdPartyBuildGenerator.java",
    ],
    main_class = "com.android.tools.binaries.ThirdPartyBuildGenerator",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        ":utils",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

# See README.md
java_binary(
    name = "add_dependency",
    srcs = [
        "src/com/android/tools/binaries/AddDependency.java",
        "src/com/android/tools/binaries/JavaImportGenerator.java",
    ],
    main_class = "com.android.tools.binaries.AddDependency",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
    deps = [
        ":maven",
        ":utils",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

java_binary(
    name = "jarjar",
    main_class = "com.tonicsystems.jarjar.Main",
    visibility = ["//visibility:public"],
    runtime_deps = [
        "//prebuilts/tools/common/m2/repository/net/sf/jopt-simple/jopt-simple/4.9:jar",
        "//prebuilts/tools/common/m2/repository/org/anarres/jarjar/jarjar-command/1.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/anarres/jarjar/jarjar-core/1.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm-commons/5.0.4:jar",
        "//prebuilts/tools/common/m2/repository/org/ow2/asm/asm/5.0.4:jar",
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-api/1.7.2:jar",
        "//prebuilts/tools/common/m2/repository/org/slf4j/slf4j-simple/1.7.2:jar",
    ],
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
        "//tools/base/bazel/repolinker:repo_linker",
        "//tools/base/build-system:gradle-tooling-api",
        "//tools/base/common:tools.common",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
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
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
)

java_binary(
    name = "antlr",
    srcs = ["src/com/android/tools/binaries/AntlrCompiler.java"],
    main_class = "com.android.tools.binaries.AntlrCompiler",
    visibility = ["//visibility:public"],
    deps = [
        ":utils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.antlr_antlr",
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
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java-util/" + PROTOC_VERSION + ":jar",
        "//prebuilts/tools/common/m2/repository/com/google/protobuf/protobuf-java/" + PROTOC_VERSION + ":jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)

java_jarjar(
    name = "studio-grpc",
    srcs = [
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-api/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-auth/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-context/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-core/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-netty/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-okhttp/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-protobuf-lite/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-protobuf/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/grpc/grpc-stub/1.21.1:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-buffer/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-http/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-http2/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec-socks/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-codec/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-common/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-handler-proxy/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-handler/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-resolver/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/netty/netty-transport/4.1.34.Final:jar",
        "//prebuilts/tools/common/m2/repository/io/opencensus/opencensus-api/0.21.0:jar",
        "//prebuilts/tools/common/m2/repository/io/opencensus/opencensus-contrib-grpc-metrics/0.21.0:jar",
    ],
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
        "//tools/base/third_party:junit_junit",
    ],
)

coverage_java_test(
    name = "bin2c_tests",
    srcs = ["testSrc/com/android/tools/binaries/Bin2CTest.java"],
    test_class = "com.android.tools.binaries.Bin2CTest",
    deps = [
        ":bin2c",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
    ],
)

coverage_java_test(
    name = "third_party_up_to_date",
    srcs = ["testSrc/com/android/tools/binaries/ThirdPartyBuildConsistencyTest.java"],
    data = [
        "//tools/base/third_party:BUILD",
        "//tools/buildSrc/base:dependencies.properties",
    ],
    tags = [
        "no_test_windows",
    ],
    test_class = "com.android.tools.binaries.ThirdPartyBuildConsistencyTest",
    deps = [
        ":maven",
        ":third_party_build_generator",
        ":utils",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:org.apache.maven_maven-aether-provider",
    ],
)

java_binary(
    name = "BazelLintWrapper",
    srcs = ["src/com/android/tools/binaries/BazelLintWrapper.java"],
    main_class = "com.android.tools.binaries.BazelLintWrapper",
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/lint/cli",
        "//tools/base/third_party:com.google.guava_guava",
    ],
)
