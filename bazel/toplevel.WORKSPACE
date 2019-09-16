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

local_repository(
      name = "windows_toolchains",
      path = "tools/base/bazel/windows_toolchains",
)

http_archive(
  name = "bazel_toolchains",
  urls = [
    "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/0.29.4.tar.gz",
    "https://github.com/bazelbuild/bazel-toolchains/archive/0.29.4.tar.gz",
  ],
  strip_prefix = "bazel-toolchains-0.29.4",
  sha256 = "ef95c8567ebf1bc7cebf77511de50d0da04a9be3b345727595e284c59a2787cf",
)

load(
  "@bazel_toolchains//repositories:repositories.bzl",
  bazel_toolchains_repositories = "repositories",
)

bazel_toolchains_repositories()

setup_external_sdk(
      name = "externsdk",
)

local_repository(
  name = "cov",
  path = "tools/base/bazel/coverage"
)
load("@cov//:results.bzl", "setup_testlogs_loop_repo")
setup_testlogs_loop_repo()
