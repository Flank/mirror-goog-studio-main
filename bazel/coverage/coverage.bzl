jacoco_cli = "@//prebuilts/tools/common/jacoco:cli"

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

    execs = ["@results//:{}.JacocoExec".format(t[2:].replace(":", "/")) for t in tests]
    native.genrule(
        name = "{}.JacocoExec".format(name),
        srcs = execs,
        outs = ["{}/jacoco.exec".format(name)],
        tools = [jacoco_cli],
        cmd = "$(location {cli}) merge --quiet $(SRCS) --destfile $@".format(cli = jacoco_cli),
    )

    native.genrule(
        name = "{}.JacocoXML".format(name),
        srcs = [
            "{}.JacocoExec".format(name),
            "@baseline//:merged-baseline-jars",
        ],
        outs = ["{}/jacoco.xml".format(name)],
        tools = [jacoco_cli],
        cmd = "$(location {cli}) report --quiet $(location {exc}) --classfiles $(location {jar}) --xml $@".format(
            cli = jacoco_cli,
            exc = "{}.JacocoExec".format(name),
            jar = "@baseline//:merged-baseline-jars",
        ),
    )

    native.genrule(
        name = "{}.lcov.unfiltered".format(name),
        srcs = [
            "@baseline//:merged-baseline-srcs",
            "{}.JacocoXML".format(name),
        ],
        outs = ["{}/lcov.unfiltered".format(name)],
        tools = ["@cov//:jacoco_xml_to_lcov"],
        cmd = "python3 $(location {xml2lcov}) {test} $(location {base}) <$(location {xml}) >$@".format(
            xml2lcov = "@cov//:jacoco_xml_to_lcov",
            test = name,
            base = "@baseline//:merged-baseline-srcs",
            xml = "{}.JacocoXML".format(name),
        ),
    )

    spi = " ".join(srcpath_include)
    spe = " ".join(["-" + x for x in srcpath_exclude])
    native.genrule(
        name = "{}.lcov.unexempted".format(name),
        srcs = ["{}.lcov.unfiltered".format(name)],
        outs = ["{}/lcov.unexempted".format(name)],
        tools = [":filter_lcov"],
        cmd = "python3 $(location :filter_lcov) <$< >$@ {} {}".format(spi, spe),
    )

    native.genrule(
        name = "{}.lcov".format(name),
        srcs = [
            "{}.lcov.unexempted".format(name),
            "@baseline//:merged-baseline-exempt_markers",
        ],
        outs = ["{}/lcov".format(name)],
        tools = [":drop_exempt"],
        cmd = "python $(location :drop_exempt) <{lcov} >$@ {em}".format(
            em = "$(location @baseline//:merged-baseline-exempt_markers)",
            lcov = "$(location {}.lcov.unexempted)".format(name),
        ),
    )

    native.genrule(
        name = "{}.lcov.notests".format(name),
        srcs = ["{}.lcov".format(name)],
        outs = ["{}/lcov.notests".format(name)],
        tools = [":merge_tests"],
        cmd = "python3 $(location :merge_tests) <$< >$@",
    )

    native.genrule(
        name = "{}.list".format(name),
        srcs = ["{}.lcov".format(name)],
        outs = ["{}/list".format(name)],
        tools = [":generate_list"],
        cmd = "python3 $(location :generate_list) <$< >$@ {}".format(name),
    )

# Combine custom coverage report definition into a format suitable for upload
#
# Arguments:
#   prefix : prefix for generated target
#   reports : list of custom coverage reports that needs to be merged
def combine_report_definitions(prefix, reports):
    native.genrule(
        name = "{}.list_all".format(prefix),
        srcs = ["{}.list".format(c) for c in reports],
        outs = ["{}/list".format(prefix)],
        cmd = "cat $(SRCS) >$@",
    )

    native.genrule(
        name = "{}.lcov_all".format(prefix),
        srcs = ["{}.lcov".format(c) for c in reports],
        outs = ["{}/lcov".format(prefix)],
        tools = [":merge_lcov"],
        cmd = "python3 $(location :merge_lcov) $(SRCS) >$@",
    )
