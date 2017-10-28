def _gradle_aar_impl(ctx):

  aar = ctx.outputs.aar

  args = []
  args += ["--out_file", aar.path]
  args += ["--gradle_file", ctx.file.build_file.path]
  args += ["--out_path", ctx.attr.aar]
  args += ["--gradle_version", ctx.attr.gradle_version]
  for repo in ctx.files.repos:
    args += ["--repo", repo.path]

  ctx.action(
    inputs = ctx.files.data + ctx.files.repos + [ctx.file.build_file],
    outputs = [aar],
    mnemonic = "gradlew",
    arguments = args,
    executable = ctx.executable._gradlew,
  )

gradle_aar = rule(
    attrs = {
        "data" : attr.label_list(allow_files = True),
        "build_file": attr.label(allow_files = True, single_file = True),
        "aar": attr.string(),
        "gradle_version": attr.string(),
        "repos": attr.label_list(allow_files = True),
        "_gradlew": attr.label(
                    executable = True,
                    cfg = "host",
                    default = Label("//tools/base/bazel:gradlew"),
                    allow_files = True
        )

    },
    outputs = {
        "aar": "%{name}.aar",
    },
    implementation = _gradle_aar_impl,
)