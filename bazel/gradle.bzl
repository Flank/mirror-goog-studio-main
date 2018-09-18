def _gradle_build_impl(ctx):
    output_file = ctx.outputs.output_file

    args = []
    args += ["--out_file", output_file.path]
    args += ["--gradle_file", ctx.file.build_file.path]
    args += ["--out_path", ctx.attr.output_file_source]
    distribution = ctx.attr._distribution.files.to_list()[0]
    args += ["--distribution", distribution.path]
    for repo in ctx.files.repos:
        args += ["--repo", repo.path]
    for task in ctx.attr.tasks:
        args += ["--task", task]

    ctx.action(
        inputs = ctx.files.data + ctx.files.repos + [ctx.file.build_file, distribution],
        outputs = [output_file],
        mnemonic = "gradlew",
        arguments = args,
        executable = ctx.executable._gradlew,
    )

gradle_build = rule(
    attrs = {
        "data": attr.label_list(allow_files = True),
        "output_file": attr.output(),
        "output_file_source": attr.string(),
        "tasks": attr.string_list(),
        "build_file": attr.label(
            allow_files = True,
            single_file = True,
        ),
        "repos": attr.label_list(allow_files = True),
        "_distribution": attr.label(
            default = Label("//tools/base/build-system:gradle-distrib"),
            allow_files = True,
        ),
        "_gradlew": attr.label(
            executable = True,
            cfg = "host",
            default = Label("//tools/base/bazel:gradlew"),
            allow_files = True,
        ),
    },
    implementation = _gradle_build_impl,
)
