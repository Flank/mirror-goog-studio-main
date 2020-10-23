def _jasmin_jar_impl(ctx):
    args = ctx.actions.args()
    args.add("-o", ctx.outputs.class_jar)
    args.add_all(ctx.files.srcs)

    ctx.actions.run(
        inputs = ctx.files.srcs,
        outputs = [ctx.outputs.class_jar],
        mnemonic = "jasmin",
        arguments = [args],
        executable = ctx.executable._jasmin,
    )

_jasmin_jar = rule(
    attrs = {
        "srcs": attr.label_list(
            non_empty = True,
            allow_files = True,
        ),
        "_jasmin": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:jasmin-compiler"),
            allow_files = True,
        ),
    },
    outputs = {
        "class_jar": "lib%{name}.jar",
    },
    implementation = _jasmin_jar_impl,
)

def jasmin_library(name, srcs = None, visibility = None):
    jar_name = "_" + name
    _jasmin_jar(
        name = jar_name,
        srcs = srcs,
    )
    native.java_import(
        name = name,
        jars = [":" + jar_name],
    )
