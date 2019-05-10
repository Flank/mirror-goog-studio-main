def setup_testlogs_loop_repo():
    native.new_local_repository(
        name = "results",
        path = "bazel-testlogs",
        build_file = "@cov//:BUILD.bazel-testlogs",
    )

# Create targets for test result files with matching suffix
#
# name : prefix for the created filegroup targets
# path_suffix : the path suffix of files we're interested in
#
# example:
# name_results(
#     name = "PRE",
#     path_suffix = "foo/bar.baz"
#     visibility = ["//default:public"],
# )
# will create public targets like:
# @results//:PRE.target/of/the/test/foo/bar.baz
#
# NB: note that the resulting target is in the top-level package
def name_results(name, path_suffix, visibility):
    for f in native.glob(["**/" + path_suffix]):
        native.filegroup(
            name = name + "." + f,
            srcs = [f],
            visibility = visibility,
        )
