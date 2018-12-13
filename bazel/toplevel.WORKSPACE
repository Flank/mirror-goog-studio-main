load("//tools/base/bazel:repositories.bzl", "setup_external_repositories")
load("//tools/base/bazel:emulator.bzl", "setup_external_sdk")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

setup_external_repositories()

local_repository(
      name = "blaze",
      path = "tools/vendor/google3/blaze",
)
load("@blaze//:binds.bzl", "blaze_binds")
blaze_binds()

local_repository(
      name = "io_bazel_rules_kotlin",
      path = "tools/external/bazelbuild-rules-kotlin",
)

http_archive(
  name = "bazel_toolchains",
  urls = [
    "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/31b5dc8c4e9c7fd3f5f4d04c6714f2ce87b126c1.tar.gz",
    "https://github.com/bazelbuild/bazel-toolchains/archive/31b5dc8c4e9c7fd3f5f4d04c6714f2ce87b126c1.tar.gz",
  ],
  strip_prefix = "bazel-toolchains-31b5dc8c4e9c7fd3f5f4d04c6714f2ce87b126c1",
  sha256 = "07a81ee03f5feae354c9f98c884e8e886914856fb2b6a63cba4619ef10aaaf0b",
)

load(
  "@bazel_toolchains//repositories:repositories.bzl",
  bazel_toolchains_repositories = "repositories",
)

bazel_toolchains_repositories()

setup_external_sdk(
      name = "externsdk",
)
