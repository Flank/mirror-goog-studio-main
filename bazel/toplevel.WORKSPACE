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
    "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/5124557861ebf4c0b67f98180bff1f8551e0b421.tar.gz",
    "https://github.com/bazelbuild/bazel-toolchains/archive/5124557861ebf4c0b67f98180bff1f8551e0b421.tar.gz",
  ],
  strip_prefix = "bazel-toolchains-5124557861ebf4c0b67f98180bff1f8551e0b421",
  sha256 = "c3b08805602cd1d2b67ebe96407c1e8c6ed3d4ce55236ae2efe2f1948f38168d",
)
