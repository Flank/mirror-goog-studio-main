def coverage_java_test(name, data = [], jvm_flags = [], visibility = None, test_excluded_packages = {}, **kwargs):
    jacoco_jvm_agent = "//prebuilts/tools/common/jacoco:agent"

    jacoco_jvm_flag = "-javaagent:$(location " + jacoco_jvm_agent + ")=destfile=$$TEST_UNDECLARED_OUTPUTS_DIR/coverage/" + name + "_tests_jacoco.exec,inclnolocationclasses=true"

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

def coverage_baseline(name, srcs = []):
    native.genrule(
        name = name + "_coverage.baseline.srcs",
        srcs = srcs,
        outs = [name + ".coverage.baseline.srcs"],
        tags = [
            "coverage-sources",
            "no_mac",
            "no_windows",
        ],
        cmd = "printf '$(RULEDIR)/%s\n' {} | sed -e 's%^$(BINDIR)/%%' >$@".format(" ".join(srcs)),
    )

    native.genrule(
        name = name + "_coverage.baseline.srcs.filtered",
        tools = ["@cov//:ignore_files_filter"],
        srcs = [name + "_coverage.baseline.srcs"],
        outs = [name + ".coverage.baseline.srcs.filtered"],
        tags = [
            "no_mac",
            "no_windows",
        ],
        cmd = "python $(location @cov//:ignore_files_filter) <$< >$@",
        visibility = ["@baseline//:__pkg__"],
    )

def coverage_java_library(name, srcs = [], **kwargs):
    native.java_library(
        name = name,
        srcs = srcs,
        **kwargs
    )

    coverage_baseline(
        name = name,
        srcs = srcs,
    )
