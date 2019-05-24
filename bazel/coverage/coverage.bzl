def coverage_report(name, tests):
    native.test_suite(
        name = name + ".suite",
        tests = ["@{}".format(t) for t in tests],
    )

    lcovs = ["@results//:{}.LCOVTracefile".format(t[2:].replace(":", "/")) for t in tests]
    native.genrule(
        name = "{}.lcov".format(name),
        srcs = lcovs,
        outs = ["{}/lcov".format(name)],
        tools = [":merge_lcov"],
        cmd = "python $(location :merge_lcov) $(SRCS) >$@",
    )
