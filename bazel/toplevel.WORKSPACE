load("//tools/base/bazel:repositories.bzl", "setup_external_repositories")
load("//tools/base/bazel:emulator.bzl", "setup_external_sdk")

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
    "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/de25e8f33fb50ba5870bed2fe644736c06afb8c5.tar.gz",
    "https://github.com/bazelbuild/bazel-toolchains/archive/de25e8f33fb50ba5870bed2fe644736c06afb8c5.tar.gz",
  ],
  strip_prefix = "bazel-toolchains-de25e8f33fb50ba5870bed2fe644736c06afb8c5",
  sha256 = "238bcd5cfe4cc79a00bb3886df089f7912ee579d955cae78eb9e9a02287a4821",
)

setup_external_sdk(
      name = "externsdk",
)
