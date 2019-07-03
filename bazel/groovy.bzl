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
        ":".join([dep.path for dep in jars.to_list()]),
    )

    ctx.actions.run(
        inputs = srcs + jars.to_list() + stub_option_files,
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

    ctx.actions.run(
        inputs = srcs + groovy_deps + option_files,
        outputs = [groovy_jar],
        mnemonic = "groovyc",
        arguments = args,
        executable = ctx.executable._groovyc,
    )
