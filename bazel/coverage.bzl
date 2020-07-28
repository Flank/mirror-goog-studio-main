def coverage_java_test(name, data = [], jvm_flags = [], visibility = None, test_excluded_packages = {}, **kwargs):
    jacoco_jvm_agent = "//prebuilts/tools/common/jacoco:agent"

    jacoco_jvm_flag = "-javaagent:$(location " + jacoco_jvm_agent + ")=destfile=$$TEST_UNDECLARED_OUTPUTS_DIR/coverage/" + name + "_tests_jacoco.exec"

    # the test needs to be visible to the results workspace it can use the deploy jar
    if visibility == None:
        visibility = ["@results//:__pkg__"]
    elif "//visibility:public" not in visibility:
        visibility += ["@results//:__pkg__"]

    native.java_test(
        name = name,
        data = data + select({
            "//tools/base/bazel:agent_coverage": [jacoco_jvm_agent],
            "//conditions:default": [],
        }),
        jvm_flags = jvm_flags + select({
            "//tools/base/bazel:agent_coverage": [jacoco_jvm_flag],
            "//conditions:default": [],
        }),
        visibility = visibility,
        **kwargs
    )

def coverage_baseline(name, srcs, jar = None, tags = None):
    # some rules produce multiple jars under their base name so this lets us overload if necessary
    if not jar:
        jar = name

    tags = tags if tags else []
    tags += [] if "no_mac" in tags else ["no_mac"]
    tags += [] if "no_windows" in tags else ["no_windows"]

    cov_sources_tags = tags + ([] if "coverage-sources" in tags else ["coverage-sources"])
    native.genrule(
        name = name + "_coverage.baseline.srcs",
        srcs = srcs,
        outs = [name + ".coverage.baseline.srcs"],
        tags = cov_sources_tags,
        cmd = "printf '$(RULEDIR)/%s\n' {} | sed -e 's%^$(BINDIR)/%%' >$@".format(" ".join(srcs)),
    )

    native.genrule(
        name = name + "_coverage.baseline.srcs.filtered",
        tools = ["@cov//:ignore_files_filter"],
        srcs = [name + "_coverage.baseline.srcs"],
        outs = [name + ".coverage.baseline.srcs.filtered"],
        tags = tags,
        cmd = "python $(location @cov//:ignore_files_filter) <$< >$@",
        visibility = ["@baseline//:__pkg__"],
    )

    native.genrule(
        name = name + "_coverage.baseline.xml",
        tools = ["@//prebuilts/tools/common/jacoco:cli"],
        srcs = [jar],
        outs = [name + ".coverage.baseline.xml"],
        tags = tags,
        cmd = "$(location {cli}) report --quiet --classfiles $< --xml $@".format(
            cli = "@//prebuilts/tools/common/jacoco:cli",
        ),
    )

    native.genrule(
        name = name + "_coverage.baseline.lcov",
        tools = ["@cov//:jacoco_xml_to_lcov"],
        srcs = [
            name + "_coverage.baseline.srcs.filtered",
            name + "_coverage.baseline.xml",
        ],
        outs = [name + ".coverage.baseline.lcov"],
        tags = tags,
        cmd = "python $(location {x2l}) {test} $(location {srcs}) <$(location {xml}) >$@".format(
            x2l = "@cov//:jacoco_xml_to_lcov",
            test = "baseline",
            srcs = name + "_coverage.baseline.srcs.filtered",
            xml = name + "_coverage.baseline.xml",
        ),
        visibility = ["@baseline//:__pkg__"],
    )

def coverage_java_library(name, srcs = [], tags = [], **kwargs):
    native.java_library(
        name = name,
        srcs = srcs,
        tags = tags,
        **kwargs
    )

    coverage_baseline(
        name = name,
        srcs = srcs,
        tags = tags,
    )
