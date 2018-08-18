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
    "https://mirror.bazel.build/github.com/bazelbuild/bazel-toolchains/archive/cdea5b8675914d0a354d89f108de5d28e54e0edc.tar.gz",
    "https://github.com/bazelbuild/bazel-toolchains/archive/cdea5b8675914d0a354d89f108de5d28e54e0edc.tar.gz",
  ],
  strip_prefix = "bazel-toolchains-cdea5b8675914d0a354d89f108de5d28e54e0edc",
  sha256 = "cefb6ccf86ca592baaa029bcef04148593c0efe8f734542f10293ea58f170715",
)
