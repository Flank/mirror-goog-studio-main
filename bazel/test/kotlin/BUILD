load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_java_library", "maven_repo", "maven_pom")

# Test just kotlin
java_library(
  name = "test_only_java",
  srcs = ["java0/pkg/J0.java"],
)

kotlin_library(
  name = "test_only_kotlin",
  srcs = ["kotlin0"],
  deps = [":test_only_java"],
)

# Test circular dependencies with kotlin and java
kotlin_library(
  name = "test_kotlin_and_java",
  srcs = ["java1_kotlin1"],
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