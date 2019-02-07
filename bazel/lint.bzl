def _lint_project_impl(ctx):
    content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
    content += "<project>\n"

    if ctx.file.baseline:
        content += "<baseline file=\"{0}\" />".format(ctx.file.baseline.path)

    content += "<module name=\"{0}\" android=\"false\" library=\"true\">\n".format(ctx.label.name)
    for file in ctx.files.srcs:
        content += "  <src file=\"{0}\"/>\n".format(file.path)
    content += "</module>\n"
    content += "</project>\n"

    project_xml = ctx.outputs.xml
    ctx.actions.write(output = project_xml, content = content)

lint_project = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = FileType([
                ".java",
                ".kt",
            ]),
        ),
        "baseline": attr.label(
            allow_single_file = True,
        ),
    },
    outputs = {
        "xml": "%{name}.xml",
    },
    implementation = _lint_project_impl,
)

def lint_test(name, srcs, baseline = None):
    project_rule_name = name + "_project"
    lint_project(
        name = project_rule_name,
        srcs = srcs,
        baseline = baseline,
    )

    data = [project_rule_name + ".xml"] + srcs
    data = data if not baseline else [baseline] + data

    native.java_test(
        name = name,
        main_class = "com.android.tools.binaries.BazelLintWrapper",
        use_testrunner = False,
        runtime_deps = ["//tools/base/bazel:BazelLintWrapper", "//tools/base/lint/cli"],
        data = data,
        args = ["$(rootpath " + project_rule_name + ".xml)"],
        tags = ["no_windows"],
    )
