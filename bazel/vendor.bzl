load("@blaze//:binds.bzl", "blaze_binds")
load("@//tools/base/bazel:repositories.bzl", "setup_vendor_repos")

def setup_vendor_repositories():
    setup_vendor_repos()
    blaze_binds()
