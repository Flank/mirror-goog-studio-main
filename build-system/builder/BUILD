load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_pom")
load("//tools/base/bazel:utils.bzl", "fileset")

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "//tools/buildSrc/base:": "com/android/builder/",
        "//prebuilts/tools/common/aapt/jni:": "",
        "src/main/resources/": "",
    },
)

kotlin_library(
    name = "builder",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        "//tools/base/build-system/profile",
        "//prebuilts/r8",
    ],
    lint_baseline = "lint_baseline.xml",
    pom = ":pom",
    resource_strip_prefix = "tools/base/build-system/builder",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/analytics-library/protos/src/main/proto",
        "//tools/analytics-library/shared:tools.analytics-shared",
        "//tools/analytics-library/tracker:tools.analytics-tracker",
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
        "//tools/base/sdk-common:tools.sdk-common",
        "//tools/base/sdklib:tools.sdklib",
        "//tools/base/signflinger",
        "//tools/base/third_party:com.google.code.findbugs_jsr305",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:com.google.protobuf_protobuf-java",
        "//tools/base/third_party:com.googlecode.json-simple_json-simple",
        "//tools/base/third_party:com.squareup_javawriter",
        "//tools/base/third_party:commons-codec_commons-codec",
        "//tools/base/third_party:it.unimi.dsi_fastutil",
        "//tools/base/third_party:javax.inject_javax.inject",
        "//tools/base/third_party:net.sf.jopt-simple_jopt-simple",
        "//tools/base/third_party:org.bouncycastle_bcpkix-jdk15on",
        "//tools/base/third_party:org.bouncycastle_bcprov-jdk15on",
        "//tools/base/third_party:org.jetbrains.kotlin_kotlin-stdlib-jdk8",
        "//tools/base/third_party:org.ow2.asm_asm",
        "//tools/base/third_party:org.ow2.asm_asm-commons",
        "//tools/base/third_party:org.ow2.asm_asm-tree",
        "//tools/base/third_party:org.ow2.asm_asm-util",
        "//tools/base/zipflinger",
    ],
)

maven_pom(
    name = "pom",
    artifact = "builder",
    group = "com.android.tools.build",
    source = "//tools/buildSrc/base:build_version",
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
        "//prebuilts/tools/common/m2/repository/com/android/tools/desugar_jdk_libs/1.1.5:jar",
        "//prebuilts/tools/common/m2/repository/com/android/tools/desugar_jdk_libs_configuration/1.1.5:jar",
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
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/27.0.1-jre:jar",
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
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/27.0.1-jre:jar",
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
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/27.0.1-jre:jar",
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
        "//prebuilts/tools/common/m2/repository/com/google/guava/guava/27.0.1-jre:jar",
        "//tools/apkzlib",
        "//tools/base/annotations",
        "//tools/base/build-system/builder",
        "//tools/base/testutils:tools.testutils",
        "//tools/base/third_party:junit_junit",
        "//tools/base/zipflinger",
    ],
)
