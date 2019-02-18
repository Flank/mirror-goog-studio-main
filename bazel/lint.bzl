def _lint_project_impl(ctx):
    content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
    content += "<project>\n"

    if ctx.file.baseline:
        content += "<baseline file=\"{0}\" />\n".format(ctx.file.baseline.path)

    content += "<module name=\"{0}\" android=\"false\" library=\"true\">\n".format(ctx.label.name)
    for file in ctx.files.srcs:
        content += "  <src file=\"{0}\"/>\n".format(file.path)
    content += "</module>\n"

    for jar in ctx.files.custom_rules:
        content += "<lint-checks jar=\"{0}\" />\n".format(jar.short_path)

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
        "custom_rules": attr.label_list(
            allow_files = True,
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

def lint_test(name, srcs, custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"], baseline = None):
    project_rule_name = name + "_project"
    lint_project(
        name = project_rule_name,
        srcs = srcs,
        baseline = baseline,
        custom_rules = custom_rules,
    )

    data = [project_rule_name + ".xml"] + srcs + custom_rules + ([baseline] if baseline else [])

    native.java_test(
        name = name,
        main_class = "com.android.tools.binaries.BazelLintWrapper",
        use_testrunner = False,
        runtime_deps = ["//tools/base/bazel:BazelLintWrapper", "//tools/base/lint/cli"],
        data = data,
        args = ["$(rootpath " + project_rule_name + ".xml)"],
        tags = ["no_windows"],
    )
