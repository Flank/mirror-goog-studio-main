load("//tools/base/bazel:repositories.bzl", "setup_external_repositories")
setup_external_repositories()

local_repository(
      name = "blaze",
      path = "tools/vendor/google3/blaze",
)
load("@blaze//:binds.bzl", "blaze_binds")
blaze_binds()

http_archive(
  name = "bazel_toolchains",
  urls = [
    "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/b49ba3689f46ac50e9277dafd8ff32b26951f82e.tar.gz",
    "https://github.com/bazelbuild/bazel-toolchains/archive/b49ba3689f46ac50e9277dafd8ff32b26951f82e.tar.gz",
  ],
  strip_prefix = "bazel-toolchains-b49ba3689f46ac50e9277dafd8ff32b26951f82e",
  sha256 = "1266f1e27b4363c83222f1a776397c7a069fbfd6aacc9559afa61cdd73e1b429",
)
