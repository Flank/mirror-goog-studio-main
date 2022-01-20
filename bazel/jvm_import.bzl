"""Provides a stripped down version of java_import."""

def _jvm_import(ctx):
    jar = ctx.files.jar[0]
    compile_jar = ctx.actions.declare_file("compiletime_" + jar.basename, sibling = jar)
    args = ctx.actions.args()
    args.add_all(["--jar", jar, "--out", compile_jar])

    # Bazel manages the class path, so we don't need this manifest attribute in
    # compile jars. If Class-Path is kept, javac will surface warnings when
    # referenced jars cannot be found. In some cases, this attribute can also
    # cause build errors (see the linked document in b/200690965#comment7).
    args.add_all(["--remove-entry", "Class-Path"])
    ctx.actions.run(
        executable = ctx.executable._modify_jar,
        arguments = [args],
        inputs = [jar],
        outputs = [compile_jar],
        mnemonic = "CreateJvmCompileJar",
        progress_message = "Creating compile jar for %s" % ctx.label,
    )

    return [
        DefaultInfo(
            files = depset([jar, compile_jar]),
        ),
        JavaInfo(
            compile_jar = compile_jar,
            output_jar = jar,
            deps = [dep[JavaInfo] for dep in ctx.attr.deps],
            exports = [export[JavaInfo] for export in ctx.attr.exports],
        ),
    ]

jvm_import = rule(
    doc = """
Imports a jar file to be used by other rules.

This differs from java_import by not running ijar to create the compile jar.
Other JVM languages (kotlin) may not be handled correctly by ijar, so this
rule can accommodate artibrarty jars.
    """,
    attrs = {
        "jar": attr.label(allow_files = [".jar"], mandatory = True),
        "deps": attr.label_list(
            default = [],
            providers = [JavaInfo],
        ),
        "exports": attr.label_list(providers = [JavaInfo]),
        "_modify_jar": attr.label(
            executable = True,
            cfg = "exec",
            default = Label("//tools/base/bazel:modify_jar_manifest"),
        ),
    },
    implementation = _jvm_import,
    provides = [JavaInfo],
)
