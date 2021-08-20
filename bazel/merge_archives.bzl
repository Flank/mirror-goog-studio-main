"""Utilities for merging jar files quickly and deterministically"""

def run_singlejar(ctx, jars, out, manifest_lines = [], extra_inputs = [], allow_duplicates = False):
    """Merge jars using the singlejar tool.

    Args:
        ctx:               the analysis context
        jars:              a list of input jars
        out:               the output jar file
        manifest_lines:    lines to put in the output manifest file
                           (manifest files in the input jars are ignored)
        extra_inputs:      additional inputs such as argfiles
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

    # TODO: Ideally singlejar would automatically merge the manifest files from
    # the input jars, but this is unimplemented in singlejar; the relevant to-do
    # comment is in output_jar.cc in the singlejar source code. To work around
    # this we have to explicitly list the lines we want in the manifest.
    if manifest_lines:
        args.add_all("--deploy_manifest_lines", manifest_lines)

    ctx.actions.run(
        inputs = jars + extra_inputs,
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
        manifest_lines = ctx.attr.manifest_lines,
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
        "manifest_lines": attr.string_list(),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _merge_jars_impl,
)
