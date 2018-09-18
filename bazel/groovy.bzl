load(":functions.bzl", "create_java_compiler_args_srcs_deps", "label_workspace_path")

def groovy_impl(ctx, roots, srcs, groovy_deps, jars, groovy_jar, stub_jar):
    #stub_args =["-o"] + [stub_jar.path] + [groovy.path for groovy in srcs]

    # jars = depset()
    # for dep in java_deps:
    #   jars += dep.transitive_runtime_jars

    stub_args, stub_option_files = create_java_compiler_args_srcs_deps(
        ctx,
        [src.path for src in srcs],
        stub_jar,
        ":".join([dep.path for dep in jars]),
    )

    ctx.action(
        inputs = srcs + list(jars) + stub_option_files,
        outputs = [stub_jar],
        mnemonic = "groovystub",
        arguments = stub_args + ["xxx"],
        executable = ctx.executable._groovystub,
    )

    merged = []
    for root in roots:
        merged += [label_workspace_path(ctx.label) + "/" + root]

    args, option_files = create_java_compiler_args_srcs_deps(
        ctx,
        [src.path for src in srcs],
        groovy_jar,
        ":".join([dep.path for dep in groovy_deps] + merged),
    )

    ctx.action(
        inputs = srcs + groovy_deps + option_files,
        outputs = [groovy_jar],
        mnemonic = "groovyc",
        arguments = args,
        executable = ctx.executable._groovyc,
    )
