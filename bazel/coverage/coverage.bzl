# Define a coverage report
#
# Arguments:
#   name : prefix of generated targets
#   tests : list of test targets run for the report
#   srcpath_include : (optional) list of path prefixes used to restrict data from matching sourcefiles
#   srcpath_exclude : (optional) list of path prefixes used to exclude data from matching sourcefiles
#
# Generated Targets:
#   name.suite : test_suite of tests
#   name.lcov.unfiltered : merger of LCOV tracefiles from individual tests
#   name.lcov : filtered version of merged LCOV tracefile
#
# Sourcepath Filtering:
#   - A path matching any prefix in srcpath_include is included
#   - A path matching any prefix in srcpath_exclude is excluded
#   - A path that is both included and excluded is treated as excluded
#   - If srcpath_included is omitted or empty then all paths are treated as included
#   - The filtered tracefile contains all paths that are included and not excluded
def coverage_report(name, tests, srcpath_include = [], srcpath_exclude = []):
    native.test_suite(
        name = name + ".suite",
        tests = ["@{}".format(t) for t in tests],
    )

    lcovs = ["@results//:{}.LCOVTracefile".format(t[2:].replace(":", "/")) for t in tests]
    native.genrule(
        name = "{}.lcov.unfiltered".format(name),
        srcs = lcovs,
        outs = ["{}/lcov.unfiltered".format(name)],
        tools = [":merge_lcov"],
        cmd = "python $(location :merge_lcov) $(SRCS) >$@",
    )

    spi = " ".join(srcpath_include)
    spe = " ".join(["-" + x for x in srcpath_exclude])
    native.genrule(
        name = "{}.lcov".format(name),
        srcs = ["{}.lcov.unfiltered".format(name)],
        outs = ["{}/lcov".format(name)],
        tools = [":filter_lcov"],
        cmd = "python $(location :filter_lcov) <$< >$@ {} {}".format(spi, spe),
    )
