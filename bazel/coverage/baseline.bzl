def setup_bin_loop_repo():
    native.new_local_repository(
        name = "baseline",
        path = "bazel-bin",
        build_file_content = """
load("@cov//:baseline.bzl", "construct_baseline_processing_graph")
construct_baseline_processing_graph()
""",
    )

# correctness requires that *.coverage.baseline.srcs be deleted
# to ensure that any deleted targets do not hang around and interfere
# studio_coverage.sh does this via a `bazel clean`
# report.sh does this via an explicit `find` and `rm`
def construct_baseline_processing_graph():
    srcs = native.glob(["**/*.coverage.baseline.srcs"])

    # turn `package/target.coverage.baseline.srcs`
    # into `package:target`
    pts = [":".join(s.rsplit("/", 1)).replace(".coverage.baseline.srcs", "") for s in srcs]

    native.genrule(
        name = "merged-baseline-srcs",
        # turn `package:target`
        # into `@//package:target_coverage.baseline.srcs.filtered`
        srcs = ["@//{}_coverage.baseline.srcs.filtered".format(pt) for pt in pts],
        outs = ["merged-baseline-srcs.txt"],
        cmd = "cat $(SRCS) | sort | uniq >$@",
        visibility = ["@results//:__pkg__"],
    )

    native.genrule(
        name = "merged-baseline-lcov",
        tools = ["@cov//:merge_lcov"],
        # turn `package:target`
        # into `@//package:target_coverage.baseline.lcov`
        srcs = ["@//{}_coverage.baseline.lcov".format(pt) for pt in pts],
        outs = ["merged-baseline.lcov"],
        cmd = "python $(location @cov//:merge_lcov) $(SRCS) >$@",
        visibility = ["@cov//:__pkg__"],
    )
