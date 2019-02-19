def _lint_project_impl(ctx):
    content = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
    content += "<project>\n"

    if ctx.file.baseline:
        content += "<baseline file=\"{0}\" />\n".format(ctx.file.baseline.path)

    for jar in ctx.files.custom_rules:
        content += "<lint-checks jar=\"{0}\" />\n".format(jar.short_path)

    content += "<module name=\"{0}\" android=\"false\" library=\"true\">\n".format(ctx.label.name)

    for file in ctx.files.srcs:
        content += "  <src file=\"{0}\" />\n".format(file.path)

    for file in ctx.files.deps:
        if not file.path.endswith(".jar"):
            fail("Not a jar: " + file.path)
        content += "  <classpath jar=\"{0}\" />\n".format(file.short_path)

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
        "custom_rules": attr.label_list(
            allow_files = True,
        ),
        "deps": attr.label_list(allow_files = True),
        "baseline": attr.label(
            allow_single_file = True,
        ),
    },
    outputs = {
        "xml": "%{name}.xml",
    },
    implementation = _lint_project_impl,
)

def lint_test(name, srcs, deps = [], custom_rules = ["//tools/base/lint:studio-checks.lint-rules.jar"], baseline = None):
    compile_deps_rule_name = name + "_compile_deps"
    native.java_binary(
        name = compile_deps_rule_name,
        runtime_deps = deps,
        main_class = "madeup",
    )
    compile_deps_jar = compile_deps_rule_name + "_deploy.jar"

    project_rule_name = name + "_project"
    lint_project(
        name = project_rule_name,
        srcs = srcs,
        deps = [compile_deps_jar],
        baseline = baseline,
        custom_rules = custom_rules,
    )

    data = [project_rule_name + ".xml"] + srcs + custom_rules + ([baseline] if baseline else []) + [compile_deps_jar]

    native.java_test(
        name = name,
        main_class = "com.android.tools.binaries.BazelLintWrapper",
        use_testrunner = False,
        runtime_deps = ["//tools/base/bazel:BazelLintWrapper", "//tools/base/lint/cli"],
        data = data,
        args = ["$(rootpath " + project_rule_name + ".xml)"],
        tags = ["no_windows"],
    )
