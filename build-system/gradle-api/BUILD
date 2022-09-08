load("//tools/base/bazel:kotlin.bzl", "kotlin_test")
load("//tools/base/bazel:maven.bzl", "maven_library")
load("//tools/base/bazel:utils.bzl", "fileset")
load("//tools/base/common:version.bzl", "BUILD_VERSION")

fileset(
    name = "resources",
    srcs = glob(["src/main/resources/**"]),
    mappings = {
        "src/main/resources/": "",
    },
)

maven_library(
    name = "gradle-api",
    srcs = glob([
        "src/main/java/**/*.kt",
        "src/main/java/**/*.java",
    ]),
    bundled_deps = [
        "//tools/base/build-system:gradle-api_neverlink",
    ],
    coordinates = "com.android.tools.build:gradle-api:" + BUILD_VERSION,
    lint_baseline = "lint_baseline.xml",
    notice = "NOTICE",
    resource_strip_prefix = "tools/base/build-system/gradle-api",
    resources = [":resources"],
    visibility = ["//visibility:public"],
    exports = [
        "@maven//:org.ow2.asm.asm",
    ],
    deps = [
        "//tools/base/annotations",  # CompileOnly should not be in pom
        "//tools/base/build-system/builder-model",  # Only present for legacy reasons. # CompileOnly should not be in pom
        "//tools/base/build-system/builder-test-api:tools.builder-test-api",
        "@maven//:com.google.guava.guava",
        "@maven//:org.jetbrains.kotlin.kotlin-stdlib-jdk8",
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
    ],
    visibility = [
        "//tools/base/build-system:__pkg__",
        "//tools/base/firebase/testlab/testlab-gradle-plugin:__pkg__",
    ],
)

filegroup(
    name = "agp_gradle_test_files",
    srcs = glob([
        "src/test/**/*.java",
        "src/test/**/*.kt",
        "src/test/resources/**",
    ]),
    visibility = ["//tools/base/build-system:__pkg__"],
)

filegroup(
    name = "agp_gradle_metalava_test_files",
    srcs = glob([
        "src/metalavaTest/**/*.kt",
        "api/**/*.txt",
    ]),
    visibility = ["//tools/base/build-system:__pkg__"],
)
