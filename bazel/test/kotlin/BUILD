load("//tools/base/bazel:kotlin.bzl", "kotlin_library", "kotlin_test", "maven_library")
load("//tools/base/bazel:maven.bzl", "maven_repo")

# Test just kotlin
java_library(
    name = "test_only_java",
    srcs = ["java0/pkg/J0.java"],
)

kotlin_library(
    name = "test_only_kotlin",
    srcs = ["kotlin0/pkg/K0.kt"],
    deps = [":test_only_java"],
)

# Test circular dependencies with kotlin and java
kotlin_library(
    name = "test_kotlin_and_java",
    srcs = [
        "java1_kotlin1/pkg/J1.java",
        "java1_kotlin1/pkg/K1.kt",
    ],
    deps = [":test_only_java"],
)

java_library(
    name = "test_java_to_kotlin",
    srcs = ["java2/pkg/J2.java"],
    deps = [
        ":test_kotlin_and_java",
        ":test_only_kotlin",
    ],
)

### Maven version:

# Test just kotlin
maven_library(
    name = "test_maven_only_java_bzl",
    srcs = ["java0/pkg/J0.java"],
    coordinates = "test_group:test_maven_only_java:1.2.3",
    legacy_name = "test_maven_only_java",
)

maven_library(
    name = "test_maven_only_kotlin_bzl",
    srcs = ["kotlin0/pkg/K0.kt"],
    coordinates = "test_group:test_maven_only_kotlin:1.2.3",
    legacy_name = "test_maven_only_kotlin",
    deps = [":test_maven_only_java_bzl"],
)

# Test circular dependencies with kotlin and java
maven_library(
    name = "test_maven_kotlin_and_java_bzl",
    srcs = [
        "java1_kotlin1/pkg/J1.java",
        "java1_kotlin1/pkg/K1.kt",
    ],
    coordinates = "test_group:test_maven_kotlin_and_java:1.2.3",
    legacy_name = "test_maven_kotlin_and_java",
    deps = [":test_maven_only_java_bzl"],
)

maven_library(
    name = "test_maven_java_to_kotlin_bzl",
    srcs = ["java2/pkg/J2.java"],
    coordinates = "test_group:test_maven_java_to_kotlin:1.2.3",
    legacy_name = "test_maven_java_to_kotlin",
    deps = [
        ":test_maven_kotlin_and_java_bzl",
        ":test_maven_only_kotlin_bzl",
    ],
)

maven_repo(
    name = "test_repo_from_kotlin",
    artifacts = [
        ":test_maven_only_kotlin",
    ],
)

# Tests

kotlin_test(
    name = "only_java_tests",
    srcs = ["java0_test/pkg/J0Test.java"],
    test_class = "pkg.J0Test",
    deps = [
        ":test_only_java",
        "//tools/base/third_party:junit_junit",
    ],
)

kotlin_test(
    name = "only_kotlin_tests",
    srcs = ["kotlin0_test/pkg/K0Test.kt"],
    test_class = "pkg.K0Test",
    deps = [
        ":test_only_kotlin",
        "//tools/base/third_party:junit_junit",
    ],
)

kotlin_test(
    name = "java_kotlin_tests",
    srcs = [
        "java1_kotlin1_test/pkg/J1Test.java",
        "java1_kotlin1_test/pkg/K1Test.kt",
    ],
    test_class = "pkg.K1Test",
    deps = [
        ":test_kotlin_and_java",
        ":test_only_java",
        ":test_only_kotlin",
        "//tools/base/third_party:junit_junit",
    ],
)
