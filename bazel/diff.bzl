def _apply_diff_impl(ctx):
    args = []
    args += ["--output_file", ctx.outputs.output_file.path]
    args += ["--input_file", ctx.file.input_file.path]
    args += ["--diff_file", ctx.file.diff_file.path]

    ctx.action(
        inputs = [ctx.file.input_file, ctx.file.diff_file],
        outputs = [ctx.outputs.output_file],
        mnemonic = "diff",
        arguments = args,
        executable = ctx.executable._apply_diff,
    )

apply_diff = rule(
    attrs = {
        "output_file": attr.output(),
        "input_file": attr.label(
            allow_files = True,
            single_file = True,
        ),
        "diff_file": attr.label(
            allow_files = True,
            single_file = True,
        ),
        "_apply_diff": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:apply_diff"),
            allow_files = True,
        ),
    },
    implementation = _apply_diff_impl,
)
