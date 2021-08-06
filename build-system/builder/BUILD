load("//tools/base/bazel:kotlin.bzl", "maven_library", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "//tools/buildSrc/base:": "com/android/builder/",
        "//prebuilts/tools/common/aapt/jni:": "",
        "src/main/resources/": "",
    },
)

maven_library(
    name = "builder_bzl",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        "//tools/base/build-system/profile",
        "//prebuilts/r8",
    ],
    coordinates = "com.android.tools.build:builder:" + BUILD_VERSION,
    legacy_name = "builder",
    lint_baseline = "lint_baseline.xml",
    module_name = "builder",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/build-system/builder",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/protos/src/main/proto:proto_bzl",
        "//tools/analytics-library/shared:tools.analytics-shared_bzl",
        "//tools/analytics-library/tracker:tools.analytics-tracker_bzl",
        "//tools/apkzlib:apkzlib_bzl",
        "//tools/base/annotations:annotations_bzl",
        "//tools/base/build-system:tools.apksig_bzl",
        "//tools/base/build-system:tools.manifest-merger_bzl",
        "//tools/base/build-system/builder-model:builder-model_bzl",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api_bzl",
        "//tools/base/common:common_bzl",
        "//tools/base/ddmlib:tools.ddmlib_bzl",
        "//tools/base/layoutlib-api:tools.layoutlib-api_bzl",
        "//tools/base/repository:tools.repository_bzl",
        "//tools/base/sdk-common:tools.sdk-common_bzl",
        "//tools/base/sdklib:tools.sdklib_bzl",
        "//tools/base/signflinger:signflinger_bzl",
        "//tools/base/zipflinger:zipflinger_bzl",
        "@maven//:com.google.code.findbugs.jsr305",
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
        "@maven//:com.googlecode.json-simple.json-simple",
        "@maven//:com.squareup.javawriter",
        "@maven//:commons-codec.commons-codec",
        "@maven//:it.unimi.dsi.fastutil",
        "@maven//:javax.inject.javax.inject",
        "@maven//:net.sf.jopt-simple.jopt-simple",
        "@maven//:org.bouncycastle.bcpkix-jdk15on",
        "@maven//:org.bouncycastle.bcprov-jdk15on",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
        "@maven//:org.ow2.asm.asm",
        "@maven//:org.ow2.asm.asm-commons",
        "@maven//:org.ow2.asm.asm-tree",
        "@maven//:org.ow2.asm.asm-util",
    ],
)

kotlin_library(
    name = "fixtures",
    srcs = glob([
        "src/testFixtures/java/**/*.kt",
        "src/testFixtures/java/**/*.java",
    ]),
    resources = glob(["src/testFixtures/resources/**"]),
    visibility = ["//visibility:public"],  # contains com.android.repository.testframework
    deps = [
        ":builder",
    ],
)

kotlin_test(
    name = "tests",
    srcs = glob([
        "src/test/java/**/*.kt",
        "src/test/java/**/*.java",
    ]),
    data = [
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//tools/base/build-system/aapt2:aapt2_for_tests",
    ],
    friends = [":builder"],
    jvm_flags = ["-Dtest.suite.jar=tests.jar"],
    resources = glob(["src/test/resources/**"]),
    tags = [
        "slow",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":builder",
        ":fixtures",
        "//prebuilts/tools/common/m2:com.android.tools.desugar_jdk_libs.1.1.5",
        "//prebuilts/tools/common/m2:com.android.tools.desugar_jdk_libs_configuration.1.1.5",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system:tools.apksig",
        "//tools/base/build-system:tools.manifest-merger",
        "//tools/base/build-system/builder-model",
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "//tools/base/common:tools.common",
        "//tools/base/ddmlib:tools.ddmlib",
        "//tools/base/layoutlib-api:tools.layoutlib-api",
        "//tools/base/repository:tools.repository",
        "//tools/base/repository:tools.testlib",
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/signflinger",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:com.google.jimfs_jimfs",
        "//tools/base/third_party:com.google.truth_truth",
        "//tools/base/third_party:junit_junit",
        "//tools/base/third_party:nl.jqno.equalsverifier_equalsverifier",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib-jdk8",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-test",
        "//tools/base/third_party:org.mockito_mockito-core",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/zipflinger",
    ],
)

java_library(
    name = "benchmarks_utils",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/StopWatch.java",
        "src/benchmarks/java/com/android/builder/benchmarks/TreeCreator.java",
        "src/benchmarks/java/com/android/builder/benchmarks/Utils.java",
        "src/benchmarks/java/com/android/builder/benchmarks/ZipCreator.java",
    ],
    deps = [
        "//tools/base/annotations",
    ],
)

java_test(
    name = "testBenchmarkAdd",
    size = "large",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/BenchmarkAdd.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testBenchmarkAdd.jar"],
    tags = [
        "manual",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":benchmarks_utils",
        "//prebuilts/tools/common/m2:com.google.guava.guava.27.0.1-jre",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testBenchmarkJarMerge",
    size = "large",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/BenchmarkJarMerge.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testBenchmarkJarMerge.jar"],
    tags = [
        "manual",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":benchmarks_utils",
        "//prebuilts/tools/common/m2:com.google.guava.guava.27.0.1-jre",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testBenchmarkList",
    size = "large",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/BenchmarkList.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testBenchmarkList.jar"],
    tags = [
        "manual",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":benchmarks_utils",
        "//prebuilts/tools/common/m2:com.google.guava.guava.27.0.1-jre",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

java_test(
    name = "testBenchmarkFolderMerge",
    size = "large",
    srcs = [
        "src/benchmarks/java/com/android/builder/benchmarks/BenchmarkFolderMerge.java",
    ],
    jvm_flags = ["-Dtest.suite.jar=testBenchmarkAdd.jar"],
    tags = [
        "manual",
    ],
    test_class = "com.android.testutils.JarTestSuite",
    deps = [
        ":benchmarks_utils",
        "//prebuilts/tools/common/m2:com.google.guava.guava.27.0.1-jre",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)

filegroup(
    name = "agp_gradle_build_files",
    srcs = glob([
        "src/main/**/*.java",
        "src/main/**/*.kt",
        "src/main/resources/**",
    ]) + [
        "build.gradle",
        "NOTICE",
        "//prebuilts/r8",
    ],
    visibility = ["//tools/base/build-system:__pkg__"],
)
