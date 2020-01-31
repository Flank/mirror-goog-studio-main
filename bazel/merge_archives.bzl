"""Utilities for merging jar files quickly and deterministically"""

def run_singlejar(ctx, jars, out, allow_duplicates = False):
    """Merge jars using the singlejar tool.

    Args:
        ctx:               the analysis context
        jars:              a list of input jars
        out:               the output jar file
        allow_duplicates:  whether to allow duplicate jar entries
                           (only one will be copied to the output)

    Expects that ctx.executable._singlejar is defined.
    """
    args = ctx.actions.args()
    args.add_all([
        "--normalize",  # Normalize timestamps to keep things deterministic.
        "--exclude_build_data",  # Exclude singlejar metadata files.
        "--dont_change_compression",  # Copy files from the inputs unchanged.
        "--add_missing_directories",  # Include parent dirs to appease tools.
    ])
    if not allow_duplicates:
        args.add("--no_duplicates")
    args.add("--output", out)
    args.add_all("--sources", jars)

    ctx.actions.run(
        inputs = jars,
        outputs = [out],
        arguments = [args],
        mnemonic = "singlejar",
        executable = ctx.executable._singlejar,
    )

def _merge_jars_impl(ctx):
    run_singlejar(
        ctx = ctx,
        jars = ctx.files.jars,
        out = ctx.outputs.out,
        allow_duplicates = ctx.attr.allow_duplicates,
    )

merge_jars = rule(
    attrs = {
        "jars": attr.label_list(
            mandatory = True,
            allow_empty = False,
            allow_files = [".jar", ".zip"],
        ),
        "out": attr.output(
            mandatory = True,
        ),
        "allow_duplicates": attr.bool(
            default = False,
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _merge_jars_impl,
)
