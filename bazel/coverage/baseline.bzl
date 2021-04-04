load("@//tools/base/bazel:merge_archives.bzl", "merge_jars")

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
        visibility = ["@cov//:__pkg__", "@results//:__pkg__"],
    )

    merge_jars(
        name = "merged-baseline-jars",
        # turn `package:target`
        # into `@//package:target_coverage.baseline.jar`
        jars = ["@//{}_coverage.baseline.jar".format(pt) for pt in pts],
        out = "merged-baseline-jars.jar",
        # use this for now b/c of duplicate META-INF/plugin.xml
        # theoretically allows for a duplicate class problem in Jacoco processing
        # however, as these are all directly from non-transitive source class jars
        # it shouldn't be a problem as we don't have overlapping source targets
        allow_duplicates = True,
        visibility = ["@cov//:__pkg__", "@results//:__pkg__"],
    )

    native.genrule(
        name = "merged-baseline-exempt_markers",
        # turn `package:target`
        # into `@//package:target_coverage.baseline.exempt_markers`
        srcs = ["@//{}_coverage.baseline.exempt_markers".format(pt) for pt in pts],
        outs = ["merged-exempt_markers.txt"],
        cmd = "cat $(SRCS) >$@",
        visibility = ["@cov//:__pkg__"],
    )
