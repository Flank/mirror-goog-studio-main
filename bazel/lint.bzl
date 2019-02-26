script_template = """\
#!/bin/bash
flags=""
if [ "$1" = "--wrapper_script_flag=--debug" ]; then
    flags="--debug"
fi
{binary} $flags {xml} {baseline}
"""

def _lint_test_impl(ctx):
    classpath = depset()
    for dep in ctx.attr.deps:
        if JavaInfo in dep:
            classpath += dep[JavaInfo].transitive_compile_time_jars

    # Create project XML:
    project_xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
    project_xml += "<project>\n"

    for jar in ctx.files.custom_rules:
        project_xml += "<lint-checks jar=\"{0}\" />\n".format(jar.short_path)

    project_xml += "<module name=\"{0}\" android=\"false\" library=\"true\">\n".format(ctx.label.name)

    for file in ctx.files.srcs:
        project_xml += "  <src file=\"{0}\" />\n".format(file.path)

    for file in classpath:
        project_xml += "  <classpath jar=\"{0}\" />\n".format(file.short_path)

    project_xml += "</module>\n"
    project_xml += "</project>\n"

    ctx.actions.write(output = ctx.outputs.project_xml, content = project_xml)

    # Create the launcher script:
    ctx.actions.write(
        output = ctx.outputs.launcher_script,
        content = script_template.format(
            binary = ctx.executable._binary.short_path,
            xml = ctx.outputs.project_xml.short_path,
            baseline = ctx.file.baseline.path if ctx.file.baseline else "",
        ),
        is_executable = True,
    )

    # Compute runfiles:
    runfiles = ctx.runfiles(
        files = (
            [ctx.outputs.project_xml, ctx.file.baseline] +
            ctx.files.srcs +
            ctx.files.custom_rules
        ),
        transitive_files = depset(
            transitive = [
                ctx.attr._binary[DefaultInfo].default_runfiles.files,
                classpath,
            ],
        ),
    )

    return [DefaultInfo(executable = ctx.outputs.launcher_script, runfiles = runfiles)]

lint_test = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "custom_rules": attr.label_list(allow_files = True),
        "deps": attr.label_list(allow_files = True),
        "baseline": attr.label(allow_single_file = True),
        "_binary": attr.label(
            executable = True,
            cfg = "target",
            default = Label("//tools/base/bazel:BazelLintWrapper"),
        ),
    },
    outputs = {
        "launcher_script": "%{name}.sh",
        "project_xml": "%{name}_project.xml",
    },
    implementation = _lint_test_impl,
    test = True,
)
