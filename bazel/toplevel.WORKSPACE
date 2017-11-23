load("//tools/base/bazel:repositories.bzl", "setup_external_repositories")
setup_external_repositories()

local_repository(
      name = "blaze",
      path = "tools/vendor/google3/blaze",
)
load("@blaze//:binds.bzl", "blaze_binds")
blaze_binds()
