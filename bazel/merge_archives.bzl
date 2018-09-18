load(":utils.bzl", "singlejar")

def _merge_archives_impl(ctx):
    ctx.action(
        inputs = ctx.files.zips,
        outputs = [ctx.outputs.output_file],
        mnemonic = "mergezips",
        arguments = [ctx.outputs.output_file.path] + [zip.path for zip in ctx.files.zips],
        executable = ctx.executable._singlejar,
    )

merge_archives = rule(
    attrs = {
        "zips": attr.label_list(allow_files = True),
        "output_file": attr.output(),
        "_singlejar": attr.label(
            default = Label("//tools/base/bazel:singlejar"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _merge_archives_impl,
)
